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

###############################################################################
### PROCESSING FUNCTIONS
###############################################################################

def run_cellpose_v3(img, model_name='cyto3', channels=[0, 1], diameter=30, use_3D=True, anisotropy=None, stitch_threshold=0, rescale=False, use_gpu=False, device=None):
    """Runs Cellpose v3 on a single image with the given parameters."""
    model = models.CellposeModel(model_type=model_name, gpu=use_gpu, device=device)
    masks, flows, styles = model.eval(img, channels=channels, diameter=diameter, do_3D=use_3D)
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
    image = flip_img(image.ndarray())
    model_name = task.parameters.get("model", "cyto3")
    task.update(f"Input image of shape: {image.shape}")
else:
    file = './sample_data/test.tif'
    image = io.imread(file)
    model_name = 'cyto3'
    channels = [0, 1]
    z_axis = 0
    diameters = 30
    use_3D = True
    anisotropy = None
    stitch_threshold = 0
    rescale = False

print(f'Starting Cellpose v3 script')

## load and run model
io.logger_setup()
use_gpu, device = get_device()
masks, flows, styles = run_cellpose_v3(
    image, 
    model_name=model_name, 
    channels=[0,1], 
    diameter=30, 
    use_3D=True, 
    anisotropy=None, 
    stitch_threshold=0, 
    rescale=False, 
    use_gpu=use_gpu, 
    device=device
    )

## save results

if appose_mode:
    task.outputs["labels"] = share_as_ndarray(flip_img(masks))
    # task.outputs["flows"] = share_as_ndarray(flip_img(flows[0]))
    # task.outputs["styles"] = share_as_ndarray(flip_img(styles))
else:
    io.imsave(f'./sample_data/test_masks.tif', masks.astype(np.uint16))
    io.imsave(f'./sample_data/test_flows.tif', flows[0].astype(np.float32))
    # io.imsave(f'./sample_data/test_styles.tif', styles.astype(np.float32))

print(f'Finished Cellpose v3 script')