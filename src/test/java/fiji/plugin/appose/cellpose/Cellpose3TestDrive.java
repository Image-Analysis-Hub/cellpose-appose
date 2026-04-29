package fiji.plugin.appose.cellpose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.cellpose.cp3.Cellpose3;
import fiji.plugin.appose.cellpose.cp3.Cellpose3Model;
import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

/**
 * Interactive tests drive for Cellpose 3, using the static methods.
 */
public class Cellpose3TestDrive
{

	public static void main( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		try
		{
			ImageJ.main( args );
			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );

//			final Cellpose3Parameters params = Cellpose3Parameters.defaultCytoParameters();
			final Cellpose3Parameters params = Cellpose3Parameters.builder()
					.model( Cellpose3Model.CYTO2 )
					.diameter( 30 )
					.build();

			final ImagePlus[] output = Cellpose3.run( imp, params );
			final ImagePlus labels = output[ 0 ];

			imp.show();
			ApposeUtils.addROIs( labels, "Cellpose" );
			RoiManager.getInstance2().runCommand( "Show All" );
			labels.show();
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
