[![build](https://github.com/Image-Analysis-Hub/cellpose-appose/actions/workflows/build.yaml/badge.svg)](https://github.com/Image-Analysis-Hub/cellpose-appose/actions/workflows/build.yaml)

# Cellpose - Appose Fiji plugin 

This is a plugin to install and run [cellpose](https://www.cellpose.org/) on 2D/3D in Fiji. 
Two version of cellpose is available:
- Cellpose (v3)
- Cellpose-SAM (v4)

This plugin is based on [Appose](https://github.com/apposed/appose), that automatically install python environment and allows python script execution with shared objects with Fiji.

## Plugin Installation

Build with :
```
mvn
``
or (to install it to Fiji directlys)
```
mvn -Dscijava.app.directory=/path/to/your/fiji
```

To install the plugin, download and copy the `.jar` file in the `plugins` directory of Fiji, and restart Fiji. The plugin should now be accessible in the plugin menu.

> [!NOTE]
> The python environment will be automatically installed in your home `.local\shared\appose` directory and activated from the plugin when needed.


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

