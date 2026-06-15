package fiji.plugin.appose.cellpose;

import fiji.plugin.appose.cellpose.cp4.Cellpose4Plugin;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.frame.Recorder;

public class Cellpose4FijiPluginDemo
{
	@SuppressWarnings( "unchecked" )
	public static void main( final String[] args )
	{
		try
		{
			ImageJ.main( args );

			// Since this is a demo in the src folder, we need to register the
			// plugin manually, as it won't be picked up by the usual plugin
			// discovery mechanism.
			ij.Menus.getCommands().put( "Cellpose 3", "fiji.plugin.appose.cellpose.cp4.Cellpose4Plugin" );

			// Switch on macro recorder.
			new Recorder();

			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			imp.show();

			new Cellpose4Plugin().run( "" );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
