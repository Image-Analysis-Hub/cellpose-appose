#@ Img image
#@output Img labels

#@ Integer (value=30) diameter
#@ Integer (value=1) cell_channel
#@ Integer (value=-1) nuclei_channel
#@ Integer (value=-1) z_axis
#@ Integer (value=0) stitch_threshold
#@ boolean (value=true) use_3D
#@ String (value='cyto3') model


import org.apposed.appose.Appose

println("== BUILDING ENVIRONMENT ==")
pixiToml = """
[workspace]
authors = [
    "Stephane Rigaud <stephane.rigaud@imba.oeaw.ac.at>", 
    "Gaelle Letort <gaelle.letort@pasteur.fr>"
    ]
channels = ["conda-forge"]
name = "cellpose-appose"
platforms = ["osx-arm64", "win-64", "linux-64", "osx-64"]
version = "0.1.0"

[tasks]
test = { cmd = "python cp3.py" }

[dependencies]
python = ">=3.10.3,<3.13"

[pypi-dependencies]
cellpose = ">=3, <4"
appose = ">=0.10.1, <0.11"
"""

env = Appose.pixi().content(pixiToml).logDebug().build()
println("Environment build complete: ${env.base()}")

// Read in the Python script (TODO: load as resource instead of hardcoding path)
cp3Path = "/Users/strigaud/Libraries/development/FijiWS/cellpose-appose/cp3.py"
cp3Script = new File(cp3Path).text
println("Loaded cp3 script of length ${cp3Script.length()}")

// Conversion functions: ImgLib2 Img <-> Appose NDArray
import net.imglib2.appose.ShmImg
imgToAppose = { img ->
	ndArray = ShmImg.copyOf(image).ndArray()
	println("Copied image into shared memory: ${ndArray.shape()}")
	return ndArray
}
import net.imglib2.appose.NDArrays
apposeToImg = { ndarray ->
	NDArrays.asArrayImg(ndarray)
}

// Run the script as an Appose task
println("== STARTING PYTHON SERVICE ==")
try (python = env.python()) {
	inputs = [
		"image": imgToAppose(image),
		"diameter": diameter,
		"cell_channel": cell_channel,
		"nuclei_channel": nuclei_channel,
        "model": model,
        "use_3D": use_3D,
        "z_axis": z_axis,
        "stitch_threshold": stitch_threshold
	]
	task = python.task(cp3Script, inputs)
		.listen { if (it.message) println("[CP3] ${it.message}") }
		.waitFor()

	println("TASK FINISHED: ${task.status}")
	if (task.error) println(task.error)
	labels = NDArrays.asArrayImg(task.outputs["labels"])
}
finally {
	println("== TERMINATING PYTHON SERVICE ==")
}