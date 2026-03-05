###############################################################################
### Cellpose v3 script for Appose
### Authors: 
###    Stephane Rigaud <stephane.rigaud@imba.oeaw.ac.at>
###    Gaelle Letort <gaelle letort.pasteur.fr>
###    Julie Mabon <julie.mabon@pasteur.fr>
###############################################################################

import torch
import numpy as np
from cellpose import models, io

report = print
def listen(callback):
    global report
    report = callback

###############################################################################
### AUXILIARY FUNCTIONS
###############################################################################

def get_device() -> tuple[bool, torch.device]:
    """Returns (use_gpu, device) using the best available backend: CUDA > MPS > CPU."""
    if torch.cuda.is_available():
        device = torch.device("cuda")
        gpu = True
        name = torch.cuda.get_device_name(0)
        print(f"Using CUDA: {name}")
    elif hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        device = torch.device("mps")
        gpu = True
        print("Using MPS (Metal)")
    else:
        device = torch.device("cpu")
        gpu = False
        print("Using CPU")
    return gpu, device

def manage_channels(cell: int|None = None, nuclei: int|None = None) -> list[int]:
    """Returns the channels list [cell_channel, nuclei_channel] for Cellpose based on the 
    provided integer values from Fiji.
    """
    if cell is not None and nuclei is not None:
        return [cell, nuclei]
    if cell is not None:
        return [cell, cell]
    if nuclei is not None:
        return [nuclei, nuclei]
    raise ValueError("At least one of 'cell' or 'nuclei' channel must be specified")
    
###############################################################################
### PROCESSING FUNCTIONS
###############################################################################

def run_cellpose_v3(img: np.ndarray, kwargs: dict) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Runs Cellpose v3 on a single image with the given parameters."""
    
    model = models.CellposeModel(
        model_type=getattr(kwargs, 'model_name', 'cyto3'),
        gpu=getattr(kwargs, 'use_gpu', False), 
        device=getattr(kwargs, 'device', None)
    )
    
    masks, flows, styles = model.eval(
        img, 
        channels=getattr(kwargs, 'channels', [0, 0]), 
        diameter=getattr(kwargs, 'diameter', 30), 
        do_3D=getattr(kwargs, 'use_3D', False), 
        anisotropy=getattr(kwargs, 'anisotropy', 1.0), 
        stitch_threshold=getattr(kwargs, 'stitch_threshold', 0.0), 
        z_axis=getattr(kwargs, 'z_axis', None),
 
        resample=getattr(kwargs, 'resample', True), 
        normalize=getattr(kwargs, 'normalize', True), 
        rescale=getattr(kwargs, 'rescale', None), 
        flow_threshold=getattr(kwargs, 'flow_threshold', 0.4), 
        cellprob_threshold=getattr(kwargs, 'cellprob_threshold', 0.0), 
        min_size=getattr(kwargs, 'min_size', 15), 
        tile_overlap=getattr(kwargs, 'tile_overlap', 0.1), 
        )
    return masks, flows, styles

###############################################################################
### MAIN PROGRAM
###############################################################################

def to_5d(arr):
    """Convert 2D or 3D array to 5D"""
    while arr.ndim < 5:
        arr = np.expand_dims(arr, axis=0)
    return arr

def flip_img(img):
    """Flips a NumPy array between Java (F_ORDER) and NumPy-friendly (C_ORDER)"""
    import numpy as np
    return np.transpose(img, tuple(reversed(range(img.ndim))))

def share_as_ndarray(img):
    """Copies a NumPy array into a same-sized newly allocated block of shared memory"""
    from appose import NDArray
    shared = NDArray(str(img.dtype), img.shape)
    shared.ndarray()[:] = img
    return shared

appose_mode = 'task' in globals()
if appose_mode:
    listen(task.update)
else:
    from appose.python_worker import Task
    task = Task()

## load images
if appose_mode:
    #input_image = flip_img(image.ndarray())
    input_image = image.ndarray()
    channels = manage_channels(cell=cell_channel, nuclei=nuclei_channel)
    stitch_threshold = stitch_threshold
    z_axis = z_axis
    anisotropy = anisotropy if anisotropy > 0 else None
    rescale = rescale
    # use_3D
    task.update(f"Input image of shape: {input_image.shape}")
else:
    file = '../../../sample_data/test.tif'
    input_image = io.imread(file)
    model = 'cyto3'
    diameter = 30
    channels = [0, 1]
    use_3D = False
    stitch_threshold = 0
    z_axis = None
    anisotropy = None
    compute_flows = True
    resample = True
    normalize = True
    rescale = None
    flow_threshold = 0.4
    cellprob_threshold = 0.0
    min_size = 15
    tile_overlap = 0.1

task.update(f"Running Cellpose v3 with model '{model}', channels {channels}, diameter {diameter}, use_3D={use_3D}, stitch_threshold={stitch_threshold}, anisotropy={anisotropy}, z_axis={z_axis}")

use_gpu, device = get_device()
masks, flows, styles = run_cellpose_v3(
    input_image,
    kwargs={
        "model_name": model,
        "channels": channels,
        "diameter": diameter,
        "use_3D": use_3D,
        "stitch_threshold": stitch_threshold,
        "anisotropy": anisotropy,
        "z_axis": z_axis,
        "use_gpu": use_gpu,
        "device": device,

        ## Advanced
        'resample': resample,
        'normalize': normalize,
        'rescale': rescale,
        'flow_threshold': flow_threshold,
        'cellprob_threshold': cellprob_threshold,
        'min_size': min_size,
        'tile_overlap': tile_overlap,
    }
)



## return output (TZCYX)
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
        io.imsave(f'../../../sample_data/test_flows.tif', flows[0].astype(np.float32))
task.update(f"Finished Cellpose v3 script")
