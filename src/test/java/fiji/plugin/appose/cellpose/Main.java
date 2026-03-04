package fiji.plugin.appose.cellpose;

import ij.IJ;
import net.imagej.ImageJ;

public class Main
{

	public static void main( final String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		IJ.openImage( "http://imagej.net/images/blobs.gif" ).show();
//		IJ.openImage( "/Users/strigaud/Libraries/development/FijiWS/cellpose-appose/sample_data/test.tif" ).show();
		ij.command().run( CellposeAppose.class, true );
	}
}
