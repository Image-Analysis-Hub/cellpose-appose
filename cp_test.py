import numpy as np
from cellpose import models, io
import torch

def get_device() -> tuple:
    """Returns the best available device: CUDA > MPS > CPU"""
    if torch.cuda.is_available():
        device = torch.device("cuda")
        print(f"Using CUDA: {torch.cuda.get_device_name(0)}")
    elif torch.backends.mps.is_available():
        device = torch.device("mps")
        print("Using MPS (Metal)")
    else:
        device = torch.device("cpu")
        print("Using CPU")
    
    return (torch.cuda.is_available() or torch.backends.mps.is_available(), device)

io.logger_setup()

print(f'Starting Cellpose v3 script')

## Parameters and inputs
files = ['./sample_data/test.tif']
model_name = 'cyto3'
channels = [[0, 1]]  # for each image, list of channels to use (cyto, nuclei)
diameters = 30
use_3D = True
use_gpu, device = get_device()

## load model
model = models.CellposeModel(gpu=device, model_type=model_name, device=device)
## load images
imgs = [io.imread(f) for f in files]
## run model
masks, flows, styles = model.eval(imgs, channels=channels, diameter=diameters, do_3D=use_3D)
## save results
for i in range(len(imgs)):
    io.imsave(f'./sample_data/test_masks_{i}.tif', masks[i].astype(np.uint16))

print(f'Finished Cellpose v3 script')