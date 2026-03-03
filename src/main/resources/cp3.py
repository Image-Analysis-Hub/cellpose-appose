###############################################################################
### Cellpose v3 script for Appose
### Authors: 
###    Stephane Rigaud <stephane.rigaud@imba.oeaw.ac.at>
###    Gaelle Letort <gaelle letort.pasteur.fr>
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

def manage_channels(cell: int = -1, nuclei: int = -1) -> list[int]:
    """Returns the channels list [cell_channel, nuclei_channel] for Cellpose based on the 
    provided integer values from Fiji.
    """
    if cell >= 0 and nuclei >= 0:
        return [cell, nuclei]
    if cell >= 0:
        return [cell, cell]
    if nuclei >= 0:
        return [nuclei, nuclei]
    raise ValueError("At least one of 'cell' or 'nuclei' channel must be specified")
    
###############################################################################
### PROCESSING FUNCTIONS
###############################################################################

def run_cellpose_v3(img, model_name, channels, diameter, use_3D, anisotropy, stitch_threshold, z_axis,use_gpu, device):
    """Runs Cellpose v3 on a single image with the given parameters."""
    model = models.CellposeModel(model_type=model_name, gpu=use_gpu, device=device)
    masks, flows, styles = model.eval(img, channels=channels, diameter=diameter, do_3D=use_3D, anisotropy=anisotropy, stitch_threshold=stitch_threshold, z_axis=z_axis)
    return masks, flows, styles

###############################################################################
### MAIN PROGRAM
###############################################################################

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
    input_image = flip_img(image.ndarray())
    channels = manage_channels(cell=cell_channel, nuclei=nuclei_channel)
    stitch_threshold = stitch_threshold if stitch_threshold >= 0 else None
    z_axis = z_axis if z_axis >= 0 else None
    anisotropy = anisotropy if anisotropy > 0 else None
    # use_3D
    # z_axis
    task.update(f"Input image of shape: {input_image.shape}")
else:
    file = './sample_data/test.tif'
    input_image = io.imread(file)
    model = 'cyto3'
    diameter = 30
    channels = [0, 1]
    use_3D = False
    stitch_threshold = 0
    z_axis = None
    anisotropy = None

task.update(f"Running Cellpose v3 with model '{model}', channels {channels}, diameter {diameter}, use_3D={use_3D}, stitch_threshold={stitch_threshold}, anisotropy={anisotropy}, z_axis={z_axis}")

use_gpu, device = get_device()
masks, flows, styles = run_cellpose_v3(
    input_image, 
    model_name=model, 
    channels=channels, 
    diameter=diameter, 
    use_3D=use_3D, 
    stitch_threshold=stitch_threshold, 
    anisotropy=anisotropy,
    z_axis=z_axis,
    use_gpu=use_gpu, 
    device=device
    )

## return output
if appose_mode:
    task.outputs["labels"] = share_as_ndarray(flip_img(masks))
    # task.outputs["flows"] = share_as_ndarray(flip_img(flows[0]))
else:
    io.imsave(f'./sample_data/test_masks.tif', masks.astype(np.uint16))
    io.imsave(f'./sample_data/test_flows.tif', flows[0].astype(np.float32))
task.update(f"Finished Cellpose v3 script")
