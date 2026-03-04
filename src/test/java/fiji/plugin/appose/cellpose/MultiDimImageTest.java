package fiji.plugin.appose.cellpose;

import ij.IJ;
import net.imagej.ImageJ;

public class MultiDimImageTest
{

	public static void main( final String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		IJ.openImage( "sample_data/test.tif" ).show();
		ij.command().run( CellposeAppose.class, true );
	}
}
