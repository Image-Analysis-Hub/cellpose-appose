[![build](https://github.com/Image-Analysis-Hub/cellpose-appose/actions/workflows/build.yaml/badge.svg)](https://github.com/Image-Analysis-Hub/cellpose-appose/actions/workflows/build.yaml)

# Cellpose - Appose Fiji plugin 

This is a plugin to install and run [cellpose](https://www.cellpose.org/) on 2D/3D in Fiji. 
Two version of cellpose is available:
- Cellpose (v3)
- Cellpose-SAM (v4)

This plugin is based on [Appose](https://github.com/apposed/appose), that automatically install python environment and allows python script execution with shared objects with Fiji.

## Plugin Installation

You can install the plugin for the unliste update site `Appose-Playground`:
in Fiji, go to `Help>Update...` then to `Manage Update Sites` in the window that opens.
Click `Add unliste update site`, name it `Appose-Playground` and write its address `https://sites.imagej.net/Appose-Playground`.

Select the Appose-Cellpose `.jar` file to install only this plugin, or keep all proposed plugins. 
Press `Apply changes` and restart Fiji when it's done.

> [!IMPORTANT]
> You should have a recent version of Fiji, based on Java 21 or more. Download a new version if you're current installation is too old.

## Usage

From Fiji
- Open the image that you want to process.  
- Launch one of the cellpose version available in the plugin:
  - `Plugins>Segmentation>Cellpose-Appose>Cellpose...`
  - `Plugins>Segmentation>Cellpose-Appose>CellposeSAM...`
- Configure your Cellpose run through the Graphic Interface
- Press "Ok" and Enjoy!   

### Additional Options

Cellpose rely on GPU acceleration through `CUDA` for windows and linux system. By default the plugin will target `CUDA 12.6`.
The use of the `CPU` or a different `CUDA` version can be set in the configuration panel:
- `Plugins>Segmentation>Cellpose-Appose>Cellpose configuration...`

> [!NOTE]
> MacOS user will automatically rely on `MPS` acceleration backend.

> [!NOTE]
> The python environment will be automatically installed in your home `.local\shared\appose` directory and activated from the plugin when needed.

## Development

To build the repository from source you will need both [Maven](https://maven.apache.org/) and [Java SDK 21](https://www.azul.com/downloads/?package=jdk#zulu) install on your system and available in your `PATH`.

At the root of the repo, run:

```bash
mvn clean install
```

You can also directly install the `.jar` in a Fiji via the option `-Dscijava.app.directory=<path/to/your/Fiji>`