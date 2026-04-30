/*-
 * #%L
 * Running Cellpose with a Fiji plugin based on Appose.
 * %%
 * Copyright (C) 2026 Appose developpers
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the My Company nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

#@ Img image
#@output Img labels

#@ String (value='cyto3') model
#@ Integer (value=30) diameter
#@ Integer (value=1) cell_channel
#@ Integer (value=-1) nuclei_channel
#@ Boolean (persist=true, value=true) use_3D
#@ Integer (value=0) stitch_threshold
#@ Integer (value=-1) z_axis
#@ Integer (value=-1) anisotropy


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
cp3Path = "/Users/strigaud/Libraries/development/FijiWS/cellpose-appose/src/main/resources/cp3.py"
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
		"model" : model,
		"diameter" : diameter,
		"cell_channel" : cell_channel,
		"nuclei_channel" : nuclei_channel,
		"use_3D" : use_3D,
		"stitch_threshold": stitch_threshold,
		"z_axis": z_axis,
		"anisotropy": anisotropy,
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
