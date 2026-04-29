import numpy as np
from cellpose import models, io
from typing import TYPE_CHECKING

report = print

def listen(callback):
    global report
    report = callback


###############################################################################
# AUXILIARY FUNCTIONS
###############################################################################

def filter_channels(selected_channels: list[int | None]) -> list[int]:
    """Filter out None values from a list of channel indices."""
    merged = [c for c in selected_channels if c is not None]
    if not merged:
        raise ValueError("At least one channel must be provided, only `None` were given.")
    return merged


###############################################################################
# PROCESSING FUNCTIONS
###############################################################################

def run_cellpose_v4(img: np.ndarray, kwargs: dict) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Runs Cellpose v4 on a single image with the given parameters. Refer to Cellpose documentation for kwargs list."""

    # Use cpsam pretrained model by default if no custom model is provided
    custom_model = kwargs.get('custom_model', None)
    selected_model = "cpsam" if custom_model is None else custom_model

    task.update(
        current = 2,
        maximum= 5,
        message=f"CP4: Deploy model {selected_model if selected_model else custom_model}"
    )
    model = models.CellposeModel(
        pretrained_model=selected_model,
        gpu=kwargs.get('use_gpu', False),
        device=kwargs.get('device', None)
    )
    task.update(
        current = 3,
        maximum= 5,
        message=f"CP4: Predict labels (device={device})"
    )
    
    masks, flows, styles = model.eval(
        img,
        diameter=kwargs.get('diameter', 30),
        do_3D=kwargs.get('use_3D', False),
        anisotropy=kwargs.get('anisotropy', 1.0),
        stitch_threshold=kwargs.get('stitch_threshold', 0.0),
        channel_axis=kwargs.get("channel_axis", None),
        z_axis=kwargs.get('z_axis', None),
        flow3D_smooth=kwargs.get('flow3D_smooth', 0),
        resample=kwargs.get('resample', True),
        normalize=kwargs.get('normalize', True),
        flow_threshold=kwargs.get('flow_threshold', 0.4),
        cellprob_threshold=kwargs.get('cellprob_threshold', 0.0),
        min_size=kwargs.get('min_size', 15),
        niter=kwargs.get( 'niter', None ),
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
    from cp_utils import get_torch_device, share_as_ndarray, make_5d
    from appose.python_worker import Task
    task = Task()

# load images
if appose_mode:
    fiji_image = globals()['image']
    stitch_threshold = globals()['stitch_threshold']
    z_axis: int | None = globals()['z_axis']
    channel_axis: int | None = globals()['channel_axis']
    time_axis: int | None = globals()['time_axis']
    anisotropy: float = globals()['anisotropy']
    diameter: int = globals()['diameter']
    use_3D: bool = globals()['use_3D']
    resample: bool = globals()['resample']
    normalize: bool = globals()['normalize']
    flow_threshold: float = globals()['flow_threshold']
    cellprob_threshold: float = globals()['cellprob_threshold']
    min_size: int = globals()['min_size']
    niter: int | None = globals()['niter']
    tile_overlap: float = globals()['tile_overlap']
    flow3D_smooth: float = globals()['flow3D_smooth']
    n_channels: int = globals()['n_channels']
    channel_axis: int | None = globals().get(
        'channel_axis', None)
    
    input_image = fiji_image.ndarray()  # pylint: disable=E1120
    anisotropy = anisotropy if anisotropy > 0 else None
    
    if channel_axis is not None:
        chan0: int | None = globals()['chan0']
        chan1: int | None = globals()['chan1']
        chan2: int | None = globals()['chan2']
        channels = filter_channels([chan0, chan1, chan2])
        if len(input_image.shape) > 2 :
            input_image = input_image[..., channels, :, :]

    if time_axis is not None:	
        if (z_axis is None) and (channel_axis is None):
            input_image = input_image[..., np.newaxis]
            channel_axis = None
        ## to use CPSAM with T+channels images, makes it as if it's 2D+stitch mode with no stitching
        elif (z_axis is None):
            z_axis = time_axis
            stitch_threshold = 1

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
    anisotropy = None
    compute_flows = True
    resample = True
    normalize = True
    flow_threshold = 0.4
    cellprob_threshold = 0.0
    min_size = 15
    niter = None
    tile_overlap = 0.1
    flow3D_smooth = 0

use_gpu, device = get_torch_device()
task.update(
    current = 1,
    maximum= 5,
    message=f"CP4: Start Cellpose (device={device})"
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
        "use_gpu": use_gpu,
        "device": device,
        'flow3D_smooth': flow3D_smooth,
        'resample': resample,
        'normalize': normalize,
        'flow_threshold': flow_threshold,
        'cellprob_threshold': cellprob_threshold,
        'min_size': min_size,
        'niter': niter,
        'tile_overlap': tile_overlap,
    }
)

task.update(
    current = 4,
    maximum = 5,
    message=f"CP4: Returning results"
)

# return output
if appose_mode:
    masks_5d = np.rollaxis(make_5d(masks), -3, -4)           # ZYX -> TZCYX
    task.outputs["labels"] = share_as_ndarray(masks_5d)      # share masks to Appose as `labels` output
    if compute_flows:
        flows_5d = np.rollaxis(make_5d(flows[0]), -1, -3)       # ZYXC -> TZCYX
        task.outputs["flows"] = share_as_ndarray(flows_5d)    # share flows to Appose as `flows` output
else:
    io.imsave(f'../../../sample_data/test_masks.tif', masks.astype(np.uint16))
    if compute_flows:
        io.imsave(f'../../../sample_data/test_flows.tif',
                flows[0].astype(np.float32))

task.update(
    current = 5,
    maximum = 5,
    message=f"CP4: Cellpose processing completed"
)
