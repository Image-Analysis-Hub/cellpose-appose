###
# #%L
# Running Cellpose with a Fiji plugin based on Appose.
# %%
# Copyright (C) 2026 My Company, Inc.
# %%
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as
# published by the Free Software Foundation, either version 2 of the
# License, or (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public
# License along with this program.  If not, see
# <http://www.gnu.org/licenses/gpl-2.0.html>.
# #L%
###
###############################################################################
### Python script to test image dimensionality for Appose
### Authors: 
###    Jean-Yves Tinevez <tinevez@pasteur.fr>
###############################################################################


img = image.ndarray()
print("Image dimensions: " + str(img.shape))

# Print the dictionary 'dims'
print("Dimensions from Fiji: " + str(dims))

task.outputs["imDim"] = img.shape
task.outputs["dims"] = str(dims)
