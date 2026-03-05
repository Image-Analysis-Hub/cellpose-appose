###############################################################################
# Cellpose v3 script for Appose
# Authors:
# Stephane Rigaud <stephane.rigaud@imba.oeaw.ac.at>
# Gaelle Letort <gaelle letort.pasteur.fr>
# Julie Mabon <julie mabon.pasteur.fr>
###############################################################################

import numpy as np
from cellpose import models, io
import cellpose
from typing import TYPE_CHECKING

report = print


def listen(callback):
    global report
    report = callback


###############################################################################
# PROCESSING FUNCTIONS
###############################################################################


def run_cellpose_v4(img: np.ndarray, kwargs: dict) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Runs Cellpose v4 on a single image with the given parameters."""

    # if z_axis, should have stitch_threshold > 0 else raises an error

    model = models.CellposeModel(
        pretrained_model="cpsam",
        gpu=kwargs.get('use_gpu', False),
        device=kwargs.get('device', None)
    )

    masks, flows, styles = model.eval(
        img,
        channel_axis=kwargs.get('channel_axis', None),
        diameter=kwargs.get('diameter', 30),
        do_3D=kwargs.get('use_3D', False),
        anisotropy=kwargs.get('anisotropy', 1.0),
        stitch_threshold=kwargs.get('stitch_threshold', 0.0),
        z_axis=kwargs.get('z_axis', None),

        resample=kwargs.get('resample', True),
        normalize=kwargs.get('normalize', True),
        rescale=kwargs.get('rescale', None),
        flow_threshold=kwargs.get('flow_threshold', 0.4),
        cellprob_threshold=kwargs.get('cellprob_threshold', 0.0),
        min_size=kwargs.get('min_size', 15),
        tile_overlap=kwargs.get('tile_overlap', 0.1),
    )
    return masks, flows, styles

###############################################################################
# MAIN PROGRAM
###############################################################################


appose_mode = 'task' in globals()
if appose_mode:
    if TYPE_CHECKING:
        from appose.python_worker import Task
        task: Task

    from appose.python_worker import Task
    task = globals()['task']
    listen(task.update)
else:
    from cp_utils import get_device, share_as_ndarray, to_5d
    from appose.python_worker import Task
    task = Task()

# load images
if appose_mode:

    image = globals()['image']
    stitch_threshold = globals()['stitch_threshold']
    z_axis: int | None = globals()['z_axis']
    anisotropy: float = globals()['anisotropy']
    rescale: float = globals()['rescale']
    diameter: int = globals()['diameter']
    use_3D: bool = globals()['use_3D']
    resample: bool = globals()['resample']
    normalize: bool = globals()['normalize']
    flow_threshold: float = globals()['flow_threshold']
    cellprob_threshold: float = globals()['cellprob_threshold']
    min_size: int = globals()['min_size']
    tile_overlap: float = globals()['tile_overlap']
    channel_axis: int | None = globals().get('channel_axis', None)  # TODO get it from java

    input_image = image.ndarray()  # pylint: disable=E1120
    anisotropy = anisotropy if anisotropy > 0 else None
    rescale = rescale
    # use_3D
    task.update(f"Input image of shape: {input_image.shape}")
else:
    file = '../../../sample_data/test.tif'
    input_image = io.imread(file)
    model = 'cyto3'
    diameter = 30
    use_3D = False
    stitch_threshold = 0.5
    z_axis = 0
    channel_axis = 1
    anisotropy = None
    compute_flows = True
    resample = True
    normalize = True
    rescale = None
    flow_threshold = 0.4
    cellprob_threshold = 0.0
    min_size = 15
    tile_overlap = 0.1

use_gpu, device = get_device()

task.update(
    f"Running Cellpose v{cellpose.version} on device {device}, diameter {diameter}, use_3D={use_3D}, stitch_threshold={stitch_threshold}, anisotropy={anisotropy}, z_axis={z_axis}")

if not use_gpu:
    task.update("WARNING: Cellpose v4 is running on CPU, it will be very slow")

masks, flows, styles = run_cellpose_v4(
    input_image,
    kwargs={
        "diameter": diameter,
        "use_3D": use_3D,
        "stitch_threshold": stitch_threshold,
        "anisotropy": anisotropy,
        "z_axis": z_axis,
        "channel_axis": channel_axis,
        "use_gpu": use_gpu,
        "device": device,

        # Advanced
        'resample': resample,
        'normalize': normalize,
        'rescale': rescale,
        'flow_threshold': flow_threshold,
        'cellprob_threshold': cellprob_threshold,
        'min_size': min_size,
        'tile_overlap': tile_overlap,
    }
)


# return output (TZCYX)
if appose_mode:
    # transform mask ZYX -> TZCYX
    masks_5d = np.rollaxis(to_5d(masks), -3, -4)
    task.outputs["labels"] = share_as_ndarray(masks_5d)
    if compute_flows:
        # transform flows ZYXC -> TZCYX
        flows_5d = np.rollaxis(to_5d(flows[0]), -1, -3)
        task.outputs["flows"] = share_as_ndarray(flows_5d)
else:
    io.imsave(f'../../../sample_data/test_masks.tif', masks.astype(np.uint16))
    if compute_flows:
        io.imsave(f'../../../sample_data/test_flows.tif',
                  flows[0].astype(np.float32))
task.update(f"Finished Cellpose v3 script")
