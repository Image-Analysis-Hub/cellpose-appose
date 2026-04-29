
package fiji.plugin.appose;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	public static boolean is3d( final ImagePlus imp )
	{
		return imp.getNSlices() > 1;
	}

	/**
	 * Returns the position at which the Z axis should be in python
	 * 
	 * @param imp
	 * @return
	 */
	public static Object getZAxis( final ImagePlus imp )
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
			return null;

		// 5D -> TZCYX
		if ( imp.getNDimensions() == 5 )
			return 1;
		// Now, 3D or 4D
		if ( imp.getNDimensions() == 3 )
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
	 * 
	 * @param inputs
	 *            the map containing all input parameters
	 */
	public static void displayParameters( final Map< String, Object > inputs )
	{
		System.out.println( "Parameters used: " );
		System.out.println( "─".repeat( 50 ) );

		inputs.forEach( ( key, value ) -> {
				System.out.printf( "  %-20s: %s%n", key, value );
		} );
		System.out.println( "─".repeat( 50 ) );
	}

	public static List< String > getChannelChoices( ImagePlus imp, boolean cp3_mode )
	{
		List< String > channelChoices = new ArrayList<>();
		for ( int i = 1; i <= imp.getNChannels(); i++ )
		{
			channelChoices.add( String.valueOf( i ) );
		}
		channelChoices.add( "None" );
		if ( cp3_mode )
			channelChoices.add( "Average" );
		return channelChoices;
	}

	public static Integer convertChannelChoiceToInt( String input, boolean cp3_mode )
	{
		if ( cp3_mode )
			return Objects.equals( input, "None" ) ? null : ( Objects.equals( input, "Average" ) ? 0 : ( input == null ? null : Integer.parseInt( input ) ) );
		return Objects.equals( input, "None" ) ? null : ( input == null ? null : Integer.parseInt( input ) -1 );
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

	public enum OperatingSystem
	{
		WINDOWS, LINUX, MACOS, UNKNOWN
	}

	/**
	 * Returns the current operating system.
	 */
	public static OperatingSystem getOperatingSystem()
	{
		final String os = System.getProperty( "os.name" ).toLowerCase();
		if ( os.contains( "mac" ) || os.contains( "darwin" ) )
			return OperatingSystem.MACOS;
		if ( os.contains( "win" ) )
			return OperatingSystem.WINDOWS;
		if ( os.contains( "nux" ) || os.contains( "nix" ) || os.contains( "aix" ) )
			return OperatingSystem.LINUX;
		return OperatingSystem.UNKNOWN;
	}

	/**
	 * Returns the CUDA version available on the system by querying
	 * {@code nvidia-smi}, or {@code null} if CUDA is not available or the OS
	 * is macOS. The returned value is already mapped to the pixi environment
	 * suffix (e.g. {@code "126"}, {@code "130"}).
	 * <p>
	 * {@code nvidia-smi} is preferred over {@code nvcc} because it reflects
	 * the driver-supported CUDA version and is present on any system with a
	 * GPU driver installed, even without the full CUDA toolkit.
	 *
	 * @return a pixi suffix string such as {@code "126"}, or {@code null}.
	 */
	public static String getCudaVersion()
	{
		if ( getOperatingSystem() == OperatingSystem.MACOS )
			return null;
		try
		{
			final ProcessBuilder pb = new ProcessBuilder( "nvidia-smi" );
			pb.redirectErrorStream( true );
			final Process process = pb.start();
			final StringBuilder output = new StringBuilder();
			try ( BufferedReader reader = new BufferedReader(
					new InputStreamReader( process.getInputStream() ) ) )
			{
				String line;
				while ( ( line = reader.readLine() ) != null )
					output.append( line ).append( "\n" );
			}
			process.waitFor();
			// nvidia-smi header contains e.g. "CUDA Version: 12.6"
			final Matcher m = Pattern
					.compile( "CUDA Version:\\s*(\\d+\\.\\d+)" )
					.matcher( output );
			if ( m.find() )
				return mapCudaVersion( m.group( 1 ) );
		}
		catch ( final IOException | InterruptedException e )
		{
			// nvidia-smi not found or failed — CUDA not available
		}
		return null;
	}

	/**
	 * Maps raw CUDA version strings (as reported by {@code nvidia-smi}) to the
	 * pixi environment suffix. Only versions listed here are supported; any other
	 * version returns {@code null}.
	 */
	static final Map< String, String > CUDA_VERSION_MAP;
	static
	{
		CUDA_VERSION_MAP = new HashMap<>();
		CUDA_VERSION_MAP.put( "12.4", "126" );
		CUDA_VERSION_MAP.put( "12.6", "126" );
		CUDA_VERSION_MAP.put( "12.8", "126" );
		// CUDA_VERSION_MAP.put( "13.0", "130" );  --- IGNORE --- feature not yet supported in the pixi.toml
	}

	/**
	 * Maps a raw CUDA version string to the pixi environment suffix using
	 * {@link #CUDA_VERSION_MAP}.
	 *
	 * @return the mapped suffix, or {@code null} if the version is not recognized.
	 */
	static String mapCudaVersion( final String rawVersion )
	{
		return CUDA_VERSION_MAP.get( rawVersion );
	}

	public static String getBestTorchConfig()
	{
		// if MacOS, return "-cpu"
		if ( getOperatingSystem() == OperatingSystem.MACOS )
			return "-cpu";
		// getCudaVersion() already returns the mapped suffix (e.g. "126")
		final String cudaVersion = getCudaVersion();
		if ( cudaVersion != null )
			return "-cu" + cudaVersion;
		// else, return "-cpu"
		return "-cpu";
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

		// no Z
		if ( imp.getNSlices() == 1 )
		{
			if (imp.getNChannels() > 1 )
			{
				if (imp.getNFrames() > 1 )
				{
					//XYCT -> TCYX
					return new ImageAxisInfo( null, 1, 0 );
				}
				//XYC -> CYX
				return new ImageAxisInfo( null, 0, null );
			}
			
			if (imp.getNFrames() > 1 )
			{
				//XYT -> TYX
				return new ImageAxisInfo( null, null, 0 );
			}
			//XY
			return new ImageAxisInfo( null, null, null );
		}

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
