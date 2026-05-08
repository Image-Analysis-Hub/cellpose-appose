package fiji.plugin.appose.cellpose;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;
import org.junit.jupiter.api.Test;

import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.roi.Regions;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.roi.geom.real.WritableSphere;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

/**
 * JUnit tests that check that the Cellpose functions can properly harness all
 * dimensionality cases.
 */
public class CellposeDimensionalitiesTest
{

	@Test
	void test() throws BuildException, IOException, InterruptedException, TaskException
	{
		final Cellpose3Parameters params = Cellpose3Parameters.builder()
				.model( Cellpose3BuiltinModels.CYTO2 )
				.channels( 1, 0 )
				.build();

		final CellposeTestDims[] toTest = CellposeTestDims.values();
//		final CellposeTestDims[] toTest = new CellposeTestDims[] { CellposeTestDims.XYZT };
		for ( final CellposeTestDims dims : toTest )
		{
			System.out.println( "Testing case " + dims.name() );

			final ImgPlus< UnsignedByteType > img = createTestImgForDims( dims );
			final ImagePlus imp = toImp( img );
			final ImagePlus[] outputs = Cellpose.cellpose3( imp, params );
			final ImagePlus label = outputs[ 0 ];

			// Test output dimensions
			final String dimNames = "XYCZT";
			final int[] inDims = imp.getDimensions();
			final int[] outDims = label.getDimensions();
			for ( int d = 0; d < dimNames.length(); d++ )
			{
				final char dimName = dimNames.charAt( d );
				if ( dimName == 'C' )
					assertEquals( 1, outDims[ d ], "For case " + dims.name() + ": Output should have only one channel." );
				else
					assertEquals( inDims[ d ], outDims[ d ], "For case " + dims.name() + ": Output and input must have the same " + dimName + " size." );
			}

			// Test calibration
			assertEquals( pixelSizeXY, label.getCalibration().pixelWidth );
			assertEquals( pixelSizeXY, label.getCalibration().pixelHeight );
			assertEquals( pixelSizeZ, label.getCalibration().pixelDepth );
			assertEquals( frameInterval, label.getCalibration().frameInterval );
			assertEquals( xyUnits, label.getCalibration().getUnit() );
			assertEquals( tUnits, label.getCalibration().getTimeUnit() );
		}
	}

	private static final long XY_SIZE = 128;

	private static final long Z_SIZE = 16;

	private static final long C_SIZE = 2;

	private static final long T_SIZE = 5;

	private static final double pixelSizeXY = 0.2;

	private static final double pixelSizeZ = 2.;

	private static final double frameInterval = 5.6;

	private static final String xyUnits = "nm";

	private static final String tUnits = "min";

	private static enum CellposeTestDims
	{
		XY( new long[] { XY_SIZE, XY_SIZE }, new AxisType[] { Axes.X, Axes.Y } ),
		XYC( new long[] { XY_SIZE, XY_SIZE, C_SIZE }, new AxisType[] { Axes.X, Axes.Y, Axes.CHANNEL } ),

		XYT( new long[] { XY_SIZE, XY_SIZE, T_SIZE }, new AxisType[] { Axes.X, Axes.Y, Axes.TIME } ),
		XYCT( new long[] { XY_SIZE, XY_SIZE, C_SIZE, T_SIZE }, new AxisType[] { Axes.X, Axes.Y, Axes.CHANNEL, Axes.TIME } ),

		XYZ( new long[] { XY_SIZE, XY_SIZE, Z_SIZE }, new AxisType[] { Axes.X, Axes.Y, Axes.Z } ),
		XYZC( new long[] { XY_SIZE, XY_SIZE, Z_SIZE, C_SIZE }, new AxisType[] { Axes.X, Axes.Y, Axes.Z, Axes.CHANNEL } ),

		// We don't test the 5D case yet. TODO
//		XYZT( new long[] { XY_SIZE, XY_SIZE, Z_SIZE, T_SIZE }, new AxisType[] { Axes.X, Axes.Y, Axes.Z, Axes.TIME } ),
//		XYZCT( new long[] { XY_SIZE, XY_SIZE, Z_SIZE, C_SIZE, T_SIZE }, new AxisType[] { Axes.X, Axes.Y, Axes.Z, Axes.CHANNEL, Axes.TIME } ),
		;

		private final long[] dims;

		private final AxisType[] axes;

		CellposeTestDims( final long[] dims, final AxisType[] axes )
		{
			this.dims = dims;
			this.axes = axes;
		}
	}

	public static ImagePlus toImp( final ImgPlus< UnsignedByteType > img )
	{
		final ImagePlus imp = ImageJFunctions.wrap( img, img.getName() );
		final int nC = ( int ) ( img.dimensionIndex( Axes.CHANNEL ) < 0 ? 1 : img.dimension( img.dimensionIndex( Axes.CHANNEL ) ) );
		final int nZ = ( int ) ( img.dimensionIndex( Axes.Z ) < 0 ? 1 : img.dimension( img.dimensionIndex( Axes.Z ) ) );
		final int nT = ( int ) ( img.dimensionIndex( Axes.TIME ) < 0 ? 1 : img.dimension( img.dimensionIndex( Axes.TIME ) ) );
		imp.setDimensions( nC, nZ, nT );
		imp.getCalibration().pixelWidth = pixelSizeXY;
		imp.getCalibration().pixelHeight = pixelSizeXY;
		imp.getCalibration().pixelDepth = pixelSizeZ;
		imp.getCalibration().frameInterval = frameInterval;
		imp.getCalibration().setUnit( xyUnits );
		imp.getCalibration().setTimeUnit( tUnits );
		return imp;
	}

	public static ImgPlus< UnsignedByteType > createTestImgForDims( final CellposeTestDims dims )
	{
		final Img< UnsignedByteType > img = ArrayImgs.unsignedBytes( dims.dims );

		String name = "testImg_";
		for ( final AxisType axis : dims.axes )
			name += axis.getLabel().substring( 0, 1 );
		final ImgPlus< UnsignedByteType > imgPlus = new ImgPlus<>( img, name, dims.axes );

		// Write a circle at every timepoint.
		writeBlob( imgPlus );
		return imgPlus;
	}

	private static void writeBlob( final ImgPlus< UnsignedByteType > imgPlus )
	{
		final int timeAxis = imgPlus.dimensionIndex( Axes.TIME );
		ImgPlus< UnsignedByteType > view = imgPlus;
		if ( timeAxis >= 0 )
		{
			for ( long t = 0; t < imgPlus.dimension( timeAxis ); t++ )
			{

				view = ImgPlusViews.hyperSlice( imgPlus, timeAxis, t );
				processTimepoint( view );
			}
		}
		else
		{
			processTimepoint( imgPlus );
		}
	}

	private static void processTimepoint( final ImgPlus< UnsignedByteType > imgPlus )
	{
		final int channelAxis = imgPlus.dimensionIndex( Axes.CHANNEL );
		if ( channelAxis < 0 )
		{
			processChannel( imgPlus );
		}
		else
		{
			// Write only in channel 0
			final ImgPlus< UnsignedByteType > view = ImgPlusViews.hyperSlice( imgPlus, channelAxis, 0 );
			processChannel( view );
		}
	}

	private static void processChannel( final ImgPlus< UnsignedByteType > imgPlus )
	{
		final int zAxis = imgPlus.dimensionIndex( Axes.Z );
		if ( zAxis < 0 )
		{
			writeCircle( imgPlus );
		}
		else
		{
			// Write cirle at the middle +/- 3
			for ( long z = Z_SIZE / 2 - 3; z <= Z_SIZE / 2 + 3; z++ )
			{
				final ImgPlus< UnsignedByteType > view = ImgPlusViews.hyperSlice( imgPlus, zAxis, z );
				writeCircle( view );
			}
		}

		// Smooth.
		Gauss3.gauss( 1., Views.extendMirrorSingle( imgPlus ), imgPlus );
	}

	private static void writeCircle( final ImgPlus< UnsignedByteType > imgPlus )
	{
		assert imgPlus.numDimensions() == 2;

		final double radius = 30.;
		final double[] center = new double[] { XY_SIZE / 2., XY_SIZE / 2. };
		final WritableSphere circle = GeomMasks.closedSphere( center, radius );
		Regions.sample( circle, imgPlus ).forEach( p -> p.set( 200 ) );
	}

	// Used for debugging only.
	public static void main( final String[] args )
	{
		final Cellpose3Parameters params = Cellpose3Parameters.builder()
				.model( Cellpose3BuiltinModels.CYTO2 )
				.channels( 1, 0 )
				.build();

		ImageJ.main( args );
		try
		{
			final CellposeTestDims[] toTest = new CellposeTestDims[] { CellposeTestDims.XYT };
			for ( final CellposeTestDims dims : toTest )
			{
				final ImgPlus< UnsignedByteType > img = createTestImgForDims( dims );
				System.out.println( '\n' + img.getName() );
				final ImagePlus imp = toImp( img );
				imp.show();
				IJ.save( imp, "samples/" + img.getName() + ".tif" );

				System.out.println( "Testing case " + dims.name() );

				final ImagePlus[] outputs = Cellpose.cellpose3( imp, params );
				final ImagePlus label = outputs[ 0 ];
				label.show();
				label.getWindow().setLocationRelativeTo( imp.getWindow() );
				IJ.save( label, "samples/" + img.getName() + "_labels.tif" );

				System.out.println( "Done." );
			}
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}

}
