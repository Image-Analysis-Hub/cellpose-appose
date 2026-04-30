###
# #%L
# Running Cellpose with a Fiji plugin based on Appose.
# %%
# Copyright (C) 2026 Appose developpers
# %%
# Redistribution and use in source and binary forms, with or without modification,
# are permitted provided that the following conditions are met:
# 
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
# 
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 
# 3. Neither the name of the My Company nor the names of its contributors
#    may be used to endorse or promote products derived from this software without
#    specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
# IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
# OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# #L%
###
import numpy as np
import torch
from cellpose import models, io
from typing import TYPE_CHECKING

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
