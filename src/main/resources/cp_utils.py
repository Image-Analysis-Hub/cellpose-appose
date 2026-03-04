
import numpy as np
import torch


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
