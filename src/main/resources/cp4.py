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
    import os
    sample_folder = '../../../samples/' # When you run this script from its location.
    test_file = 'testImg_XYCT.tif'
    file = os.path.join(sample_folder, test_file) 
    input_image = io.imread(file)
    custom_model = None
    diameter = 30
    use_3D = False
    stitch_threshold = 0.5
    z_axis = None
    channel_axis = 1
    time_axis = 0
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
    task.update(message = f'Input image shape: {input_image.shape}')
    task.update(message = f'Masks shape: {masks.shape}')
    masks_5d = make_5d(masks, z_axis=z_axis, time_axis=time_axis)
    task.outputs["labels"] = share_as_ndarray(masks_5d)      # share masks to Appose as `labels` output
    if compute_flows:
        flows_5d = make_5d(flows[0], z_axis=z_axis, time_axis=time_axis)
        task.outputs["flows"] = share_as_ndarray(flows_5d)    # share flows to Appose as `flows` output
else:
    save_path = os.path.join(sample_folder, test_file.replace('.tif', '_masks.tif'))
    io.imsave(save_path, masks.astype(np.uint16))
    if compute_flows:
        flow_save_path = os.path.join(sample_folder, test_file.replace('.tif', '_flows.tif'))
        io.imsave(flow_save_path, flows[0].astype(np.float32))

task.update(
    current = 5,
    maximum = 5,
    message=f"CP4: Cellpose processing completed"
)
