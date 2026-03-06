[![build](https://github.com/Image-Analysis-Hub/cellpose-appose/actions/workflows/build.yaml/badge.svg)](https://github.com/Image-Analysis-Hub/cellpose-appose/actions/workflows/build.yaml)

# Cellpose - Appose Fiji plugin 

This is a plugin to install and run [cellpose](https://www.cellpose.org/) on 2D/3D/temporal images/movies in Fiji.  

This plugin is based on [Appose](https://github.com/apposed/appose), that automatically install python environement and allows python script execution with shared objects with Fiji.

## Plugin Installation

To install the plugin, download and copy the `.jar` file in the `plugins` directory of Fiji, and restart Fiji. The plugin should now be accessible in the plugin menu.

> [!NOTE]
> The python environment will be automatically installed in your home `.local\shared\appose` directory and activated from the plugin when needed.

## Usage

From Fiji, open the image that you want to process.  
Launch the plugin from `Plugins>Cellpose-Appose>cellpose appose`.  
An interface will pop-up to let you choose the parameters to run Cellpose.  
Enjoy!   

