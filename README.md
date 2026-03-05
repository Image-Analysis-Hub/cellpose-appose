Fiji plugin to run Cellpose with Appose

This plugin is based on [Appose](https://github.com/apposed/appose), that allows to install python environement and run python script with shared objects.
It can run [cellpose](https://www.cellpose.org/) on 2D/3D/temporal images/movies. 

## Installation

To install, copy the `.jar` file in the `plugins` directory of Fiji. Restart Fiji.
Thanks to Appose magic, the python environment will be automatically installed in your home `.local\shared\appose` directory and called from the plugin.

## Usage

Start Fiji, open the image that you want to process.
Go to `Plugins>Cellpose-Appose>cellpose appose`.
An interface will pop-up to let you choose the parameters to run Cellpose.

## Running from python (not through Fiji, for tests) 
## Python env install:
Run:
```pixi install```

## Python test run
```pixi run test```
