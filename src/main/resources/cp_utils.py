import numpy as np
import torch

def make_5d(arr: np.ndarray) -> np.ndarray:
    """Convert NumPy array to 5D NumPy array, adding singleton dimensions as needed."""
    while arr.ndim < 5:
        arr = np.expand_dims(arr, axis=0)
    return arr


def flip_image(image: np.ndarray) -> np.ndarray:
    """Flips a NumPy array between Java (F_ORDER) and NumPy-friendly (C_ORDER)"""
    return np.transpose(image, tuple(reversed(range(image.ndim))))


def share_as_ndarray(arr: np.ndarray) -> 'NDArray':
    """Copies a NumPy array into a same-sized newly allocated block of shared memory"""
    from appose import NDArray
    shared = NDArray(str(arr.dtype), arr.shape)
    shared.ndarray()[:] = arr
    return shared


def get_torch_device() -> tuple[bool, torch.device]:
    """Check torch device availability and returns a tupple (use_gpu: bool, device: torch.device) using the best available backend: CUDA > MPS > CPU."""
    if torch.cuda.is_available():
        device = torch.device("cuda")
        gpu = True
    elif hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        device = torch.device("mps")
        gpu = True
    else:
        device = torch.device("cpu")
        gpu = False
    return gpu, device
