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

> [!NOTE]
> You should have a recent version of Fiji, based on Java 21 or more. Download a new version if you're current installation is too old.

## Usage

From Fiji
- Open the image that you want to process.  
- Launch one of the cellpose version available in the plugin:
  - `Plugins>Cellpose-Appose>cellpose appose`
  - `Plugins>Cellpose-Appose>cellpose-sam appose`
- Configure your Cellpose run through the Graphic Interface
- Press "Ok" and Enjoy!   

> [!NOTE]
> Be aware that the first run can be a bit long as the model has to be downloaded.

> [!NOTE]
> The python environment will be automatically installed in your home `.local\shared\appose` directory and activated from the plugin when needed.
