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