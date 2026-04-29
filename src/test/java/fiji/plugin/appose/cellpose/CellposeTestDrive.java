package fiji.plugin.appose.cellpose;

import java.awt.Color;
import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import fiji.plugin.appose.cellpose.cp4.Cellpose4Parameters;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

/**
 * Interactive tests drive for Cellpose 3, using the static methods.
 */
public class CellposeTestDrive
{

	public static void main( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		try
		{
			ImageJ.main( args );
			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			imp.show();

			/*
			 * Cellpose 3
			 */

//			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.defaultCytoParameters();
			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.builder()
					.model( Cellpose3BuiltinModels.CYTO2 )
					.diameter( 30 )
					.build();
			final ImagePlus[] outputCP3 = Cellpose.cellpose3( imp, paramsCP3 );
			final ImagePlus labelsCP3 = outputCP3[ 0 ];

			IJ.selectWindow( imp.getID() );
			ApposeUtils.addROIs( labelsCP3, "Cellpose-3", Color.BLUE );
			RoiManager.getInstance2().runCommand( "Show All" );
			labelsCP3.show();
			
			/*
			 * Cellpose 4
			 */

			final Cellpose4Parameters paramsCP4 = Cellpose4Parameters.defaultParameters();
			final ImagePlus[] outputCP4 = Cellpose.cellpose4( imp, paramsCP4 );
			final ImagePlus labelsCP4 = outputCP4[ 0 ];

			IJ.selectWindow( imp.getID() );
			ApposeUtils.addROIs( labelsCP4, "Cellpose-SAM", Color.RED );
			RoiManager.getInstance2().runCommand( "Show All" );
			labelsCP4.show();
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
