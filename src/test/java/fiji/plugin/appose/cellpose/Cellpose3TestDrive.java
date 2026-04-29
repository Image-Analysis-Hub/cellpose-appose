package fiji.plugin.appose.cellpose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.cellpose.cp3.Cellpose3;
import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.ImageJ;

/**
 * Interactive tests drive for Cellpose 3, using the static methods.
 */
public class Cellpose3TestDrive
{

	public static void main( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );

		final Cellpose3Parameters params = Cellpose3Parameters.defaultCytoParameters();
		final ImagePlus[] output = Cellpose3.run( imp, params );
		output[ 0 ].show();
	}
}
