package fiji.plugin.appose.cellpose;

import fiji.plugin.appose.cellpose.cp3.CellposeAppose;
import ij.IJ;
import net.imagej.ImageJ;

public class Main
{

	public static void main( final String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		IJ.openImage( "http://imagej.net/images/blobs.gif" ).show();
//		IJ.openImage( "sample_data/test.tif" ).show();
		ij.command().run( CellposeAppose.class, true );
	}
}
