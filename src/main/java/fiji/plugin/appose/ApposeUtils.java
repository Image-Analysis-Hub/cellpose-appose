package fiji.plugin.appose;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import fiji.plugin.appose.RoiUtils.LabelMapToPolygons;
import fiji.plugin.appose.RoiUtils.Polygon2D;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imagej.ImgPlus;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.numeric.real.DoubleType;

public class ApposeUtils
{

	/**
	 * A utility to wrap an ImagePlus into an ImgPlus, without too many
	 * warnings. Hacky.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static final < T > ImgPlus< T > rawWraps( final ImagePlus imp )
	{
		final ImgPlus< DoubleType > img = ImagePlusAdapter.wrapImgPlus( imp );
		final ImgPlus raw = img;
		return raw;
	}

	private static LUT loadLutFromResource( final String resourcePath )
	{
		try (InputStream is = ApposeUtils.class.getResourceAsStream( resourcePath );
				BufferedReader reader = new BufferedReader( new InputStreamReader( is ) ))
		{

			if ( is == null )
			{
				IJ.error( "LUT resource not found: " + resourcePath );
				return null;
			}

			final byte[] reds = new byte[ 256 ];
			final byte[] greens = new byte[ 256 ];
			final byte[] blues = new byte[ 256 ];
			String line;
			int index = 0;

			while ( ( line = reader.readLine() ) != null && index < 256 )
			{
				line = line.trim();
				if ( line.isEmpty() )
					continue; // Skip empty lines

				// Split by whitespace
				final String[] parts = line.split( "\\s+" );
				if ( parts.length >= 3 )
				{
					reds[ index ] = ( byte ) Integer.parseInt( parts[ 0 ] );
					greens[ index ] = ( byte ) Integer.parseInt( parts[ 1 ] );
					blues[ index ] = ( byte ) Integer.parseInt( parts[ 2 ] );
					index++;
				}
			}

			if ( index != 256 )
			{
				IJ.error( "Invalid LUT file: expected 256 entries, found " + index );
				return null;
			}

			return new LUT( reds, greens, blues );
		}
		catch ( final IOException e )
		{
			IJ.error( "Failed to load LUT: " + e.getMessage() );
			return null;
		}
	}

	public static final void useGlasbeyDarkLUT( final ImagePlus imp )
	{
		final LUT lut = loadLutFromResource( "/glasbey_on_dark.lut" );
		useLUT( imp, lut );
	}

	public static final void useLUT( final ImagePlus imp, final LUT lut )
	{
		imp.setLut( lut );
		imp.updateAndDraw();
	}

	/**
	 * Transfers the calibration of an {@link ImagePlus} to another one,
	 * generated from a capture of the first one.
	 *
	 * @param from
	 *            the imp to copy from.
	 * @param to
	 *            the imp to copy to.
	 */
	public static final void transferCalibration( final ImagePlus from, final ImagePlus to )
	{
		final Calibration fc = from.getCalibration();
		final Calibration tc = to.getCalibration();

		tc.setUnit( fc.getUnit() );
		tc.setTimeUnit( fc.getTimeUnit() );
		tc.frameInterval = fc.frameInterval;

		tc.pixelWidth = fc.pixelWidth;
		tc.pixelHeight = fc.pixelHeight;
		tc.pixelDepth = fc.pixelDepth;
	}
	
	/*
	 * Check if the Image is 3D or 2D
	 */
	public static boolean is3d(final ImagePlus imp)
	{
		return imp.getNSlices() > 1;
	}
	
	/**
	 * Returns the position at which the Z axis should be in python
	 * @param imp
	 * @return
	 */
	public static Object getZAxis( final ImagePlus imp )
	{
		// print info about the image in the log
		System.out.println("─".repeat(50));
		System.out.println("Image dimension: ");
		System.out.println("\t"+imp.getNSlices()+" Z slices");
		System.out.println("\t"+imp.getNChannels()+" C channels");
		System.out.println("\t"+imp.getNFrames()+" T frames");
		System.out.println("─".repeat(50));
		
		// 2D, easy peasy
		if ( imp.getNSlices() == 1 )
				return null;
		
		// 5D -> TZCYX
		if ( imp.getNDimensions() == 5 )
			return 1;
		// Now, 3D or 4D
		if ( imp.getNDimensions()==3 )
		{
			// ZYX
			return 0;
		}
		// if Z and T, TZYX
		if ( imp.getNFrames() > 1 )
			return 1;
		// XYZC is left -> Z,C,Y,X
		return 0;
	}
	
	/**
	 * Displays the parameters used in a formatted manner
	 * @param inputs the map containing all input parameters
	 */
	public static void displayParameters(final Map<String, Object> inputs) {
		System.out.println("Parameters used: ");
		System.out.println("─".repeat(50));

		inputs.forEach((key, value) -> {
			if (!key.equals("image") && !key.equals("cell_channel") && !key.equals("nuclei_channel")) {
				System.out.printf("  %-20s: %s%n", key, value);
			}
		});

		// Add combined channel line
		final Object cellChannel = inputs.get("cell_channel");
		final Object nucleiChannel = inputs.get("nuclei_channel");
		System.out.printf("  %-20s: [%d, %d]%n", "channel[cell,nuclei]", cellChannel, nucleiChannel);

		System.out.println("─".repeat(50));
	}
	
	public static List<String> getChannelChoices(ImagePlus imp){
		List< String > channelChoices = new ArrayList<>();
		for ( int i = 1; i <= imp.getNChannels(); i++ )
		{
			channelChoices.add( String.valueOf( i ) );
		}
		channelChoices.add( "None" );
		return channelChoices;
	}
	
	public static Integer convertChannelChoiceToInt(String input) {
        return Objects.equals(input, "None") ? null :
               (input == null ? null : Integer.parseInt(input) - 1);
    }


	public static void addROIs( ImagePlus labels )
	{
		// from
		// https://github.com/ijpb/MorphoLibJ/blob/master/src/main/java/inra/ijpb/plugins/LabelMapToPolygonRois.java

		ImageProcessor image = labels.getProcessor();

		int conn = 4;
		LabelMapToPolygons.VertexLocation loc = LabelMapToPolygons.VertexLocation.CORNER;
		String pattern = "r%03d";

		// compute boundaries
		LabelMapToPolygons tracker = new LabelMapToPolygons( conn, loc );
		Map< Integer, ArrayList< Polygon2D > > boundaries = tracker.process( image );

		RoiManager rm = RoiManager.getInstance();
		if ( rm == null )
		{
			rm = new RoiManager();
		}
		// populate RoiManager with PolygonRoi
		for ( int label : boundaries.keySet() )
		{
			ArrayList< Polygon2D > polygons = boundaries.get( label );
			String name = String.format( pattern, label );

			if ( polygons.size() == 1 )
			{
				PolygonRoi roi = polygons.get( 0 ).createRoi();
				roi.setName( name );
				rm.addRoi( roi );
			}
			else
			{
				int index = 0;
				for ( Polygon2D poly : polygons )
				{
					PolygonRoi roi = poly.createRoi();
					roi.setName( name + "-" + ( index++ ) );
					rm.addRoi( roi );
				}
			}
		}
	}

	public static ImageAxisInfo getImageAxisInfo( final ImagePlus imp )
	{
		// print info about the image in the log
		System.out.println( "─".repeat( 50 ) );
		System.out.println( "Image dimension: " );
		System.out.println( "\t" + imp.getNSlices() + " Z slices" );
		System.out.println( "\t" + imp.getNChannels() + " C channels" );
		System.out.println( "\t" + imp.getNFrames() + " T frames" );
		System.out.println( "─".repeat( 50 ) );

		// 2D, easy peasy
		if ( imp.getNSlices() == 1 )
			return new ImageAxisInfo( null, null, null );

		// 5D -> TZCYX
		if ( imp.getNDimensions() == 5 )
			return new ImageAxisInfo( 1, 2, 0 );
		// Now, 3D or 4D
		if ( imp.getNDimensions() == 3 )
		{
			// ZYX
			return new ImageAxisInfo( 0, null, null );
		}
		// if Z and T, TZYX
		if ( imp.getNFrames() > 1 )
			return new ImageAxisInfo( 1, null, 0 );
		// XYZC is left -> Z,C,Y,X
		return new ImageAxisInfo( 0, 1, null );
	}
}
