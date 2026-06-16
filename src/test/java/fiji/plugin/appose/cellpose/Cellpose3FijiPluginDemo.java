package fiji.plugin.appose.cellpose;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import fiji.plugin.appose.cellpose.cp3.Cellpose3Plugin;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.frame.Recorder;

public class Cellpose3FijiPluginDemo
{
	@SuppressWarnings( "unchecked" )
	public static void main( final String[] args )
	{
		setLF();
		try
		{
			ImageJ.main( args );

			// Since this is a demo in the src folder, we need to register the
			// plugin manually, as it won't be picked up by the usual plugin
			// discovery mechanism.
			ij.Menus.getCommands().put( "Cellpose 3", "fiji.plugin.appose.cellpose.cp3.Cellpose3Plugin" );

			// Switch on macro recorder.
			new Recorder();

			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			imp.show();

			new Cellpose3Plugin().run( "" );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}

	static void setLF()
	{
		try
		{
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		}
		catch ( ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e )
		{
			e.printStackTrace();
		}

	}
}
