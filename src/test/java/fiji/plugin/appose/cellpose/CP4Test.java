package fiji.plugin.appose.cellpose;

import fiji.plugin.appose.cellpose.cp4.CellposeSAMAppose;
import ij.IJ;
import net.imagej.ImageJ;

public class CP4Test
{

	public static void main( final String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		IJ.openImage( "http://imagej.net/images/blobs.gif" ).show();
//		IJ.openImage( "/Users/strigaud/Libraries/development/FijiWS/cellpose-appose/sample_data/test.tif" ).show();
		ij.command().run( CellposeSAMAppose.class, true );
	}
}