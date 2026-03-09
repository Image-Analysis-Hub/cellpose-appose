###############################################################################
# Cellpose v3 script for Appose
# Authors:
# Stephane Rigaud <stephane.rigaud@imba.oeaw.ac.at>
# Gaelle Letort <gaelle letort.pasteur.fr>
# Julie Mabon <julie mabon@pasteur.fr>
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

def merge_channels(selected_channels: list[int | None]):
    chan_merged = []
    for c in selected_channels:
        if c is not None:
            chan_merged.append(c-1)
    assert len(chan_merged) > 0, "at least one channel should be not None"
    return chan_merged


def run_cellpose_v4(img: np.ndarray, kwargs: dict) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Runs Cellpose v4 on a single image with the given parameters."""

    custom_model = kwargs.get('custom_model', None)
    model = "cpsam" if custom_model is None else custom_model
    task.update(
        current = 2,
        maximum= 5,
        message=f"CP4: Deploy model {model}"
    )
    model = models.CellposeModel(
        pretrained_model=model,
        gpu=kwargs.get('use_gpu', False),
        device=kwargs.get('device', None)
    )
    task.update(
        current = 3,
        maximum= 5,
        message=f"CP4: Predict labels"
    )
    masks, flows, styles = model.eval(
        img,
        diameter=kwargs.get('diameter', 30),
        do_3D=kwargs.get('use_3D', False),
        anisotropy=kwargs.get('anisotropy', 1.0),
        stitch_threshold=kwargs.get('stitch_threshold', 0.0),
        channel_axis=kwargs.get('channel_axis', None),
        z_axis=kwargs.get('z_axis', None),
        flow3D_smooth=kwargs.get('flow3D_smooth', 0),
        resample=kwargs.get('resample', True),
        normalize=kwargs.get('normalize', True),
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
    channel_axis: int | None = globals()['channel_axis']
    anisotropy: float = globals()['anisotropy']
    diameter: int = globals()['diameter']
    use_3D: bool = globals()['use_3D']
    resample: bool = globals()['resample']
    normalize: bool = globals()['normalize']
    flow_threshold: float = globals()['flow_threshold']
    cellprob_threshold: float = globals()['cellprob_threshold']
    min_size: int = globals()['min_size']
    tile_overlap: float = globals()['tile_overlap']
    flow3D_smooth: float = globals()['flow3D_smooth']
    n_channels: int = globals()['n_channels']
    channel_axis: int | None = globals().get(
        'channel_axis', None)  # TODO get it from java

    input_image = image.ndarray()  # pylint: disable=E1120
    anisotropy = anisotropy if anisotropy > 0 else None
    # use_3D
    
    if n_channels > 3:
        chan0: int | None = globals()['chan0']
        chan1: int | None = globals()['chan1']
        chan2: int | None = globals()['chan2']
        channels = merge_channels([chan0, chan1, chan2])
        input_image = input_image[..., channels, :, :]
    task.update(
        current = 0,
        maximum = 5,
        message = f"CP4: Fetch image from Fiji ({input_image.shape})"
        )
else:
    file = '../../../sample_data/test.tif'
    input_image = io.imread(file)
    custom_model = None
    model = 'cyto3'
    diameter = 30
    use_3D = False
    stitch_threshold = 0.5
    z_axis = 0
    channel_axis = 1
    channel_axis = 1
    anisotropy = None
    compute_flows = True
    resample = True
    normalize = True
    flow_threshold = 0.4
    cellprob_threshold = 0.0
    min_size = 15
    tile_overlap = 0.1
    flow3D_smooth = 0

use_gpu, device = get_device()

task.update(
    current = 1,
    maximum= 5,
    message=f"CP3: Start Cellpose script (device={device})"
)

masks, flows, styles = run_cellpose_v4(
    input_image,
    kwargs={
        "diameter": diameter,
        "use_3D": use_3D,
        "stitch_threshold": stitch_threshold,
        "anisotropy": anisotropy,
        "channel_axis": channel_axis,
        "z_axis": z_axis,
        "channel_axis": channel_axis,
        "use_gpu": use_gpu,
        "device": device,
        'flow3D_smooth': flow3D_smooth,
        'resample': resample,
        'normalize': normalize,
        'flow_threshold': flow_threshold,
        'cellprob_threshold': cellprob_threshold,
        'min_size': min_size,
        'tile_overlap': tile_overlap,
    }
)

task.update(
    current = 4,
    maximum = 5,
    message=f"CP4: Returning results"
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

task.update(
    current = 5,
    maximum = 5,
    message=f"CP4: Finished Cellpose script"
)
