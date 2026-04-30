
package fiji.plugin.appose;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.apposed.appose.TaskEvent;
import org.scijava.Context;

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
import net.imagej.ImgPlusMetadata;
import net.imagej.axis.Axes;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.numeric.real.DoubleType;

public class ApposeUtils
{

	private static Context context;

	/**
	 * Obtains and cache the SciJava {@link Context} in use by ImageJ.
	 *
	 * @return the SciJava context
	 */
	public static Context getContext()
	{
		final Context localContext = context;
		if ( localContext != null )
			return localContext;

		synchronized ( ApposeUtils.class )
		{
			if ( context == null )
				context = ( Context ) IJ.runPlugIn( "org.scijava.Context", "" );
			return context;
		}
	}

	/**
	 * Forwards Appose task events to an ImageJ status bar.
	 * 
	 * @return a consumer of Appose task events that updates the given task
	 *         accordingly.
	 */
	public static Consumer< TaskEvent > ijTaskListener()
	{
		return e -> {

			long maximum = 100;

			if ( e.message != null )
				IJ.showStatus( e.message );

			if ( e.maximum >= 0 )
				maximum = e.maximum;

			if ( e.current >= 0 )
				IJ.showProgress( ( double ) e.current / maximum );
		};
	}

	public static class ApposeLogger
	{

		private volatile JDialog progressDialog;

		private volatile JProgressBar progressBar;

		private volatile ScheduledFuture< ? > delayedShowTask;

		private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool( 1 );

		public void close()
		{
			EventQueue.invokeLater( () -> {
				// Cancel the delayed show if it hasn't run yet
				if ( delayedShowTask != null )
				{
					delayedShowTask.cancel( false );
					delayedShowTask = null;
				}

				if ( progressDialog != null )
					progressDialog.dispose();
				progressDialog = null;
			} );
		}

		public void showProgress( final String msg )
		{
			showProgress( msg, null, null );
		}

		public void showProgress( final String msg, final Long cur, final Long max )
		{
			EventQueue.invokeLater( () -> {
				if ( progressDialog == null )
				{
					// Schedule the dialog to appear after 1 second
					if ( delayedShowTask == null )
					{
						delayedShowTask = scheduler.schedule( () -> {
							EventQueue.invokeLater( () -> {
								if ( progressDialog == null )
								{
									createAndShowDialog();
								}
							} );
						}, 1, TimeUnit.SECONDS );
					}
					return; // Don't update yet, dialog not visible
				}

				// Update existing dialog
				updateProgressBar( msg, cur, max );
			} );
		}

		private void createAndShowDialog()
		{
			final Window owner = IJ.getInstance();
			progressDialog = new JDialog( owner, "Fiji ♥ Appose" );
			progressDialog.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
			progressBar = new JProgressBar();
			progressDialog.getContentPane().add( progressBar );
			progressBar.setFont( new Font( "Courier", Font.PLAIN, 14 ) );
			progressBar.setString(
					"--------------------==================== " +
							"Building Python environment " +
							"====================--------------------" );
			progressBar.setStringPainted( true );
			progressBar.setIndeterminate( true );
			progressDialog.pack();
			progressDialog.setLocationRelativeTo( owner );
			progressDialog.setVisible( true );
			delayedShowTask = null;
		}

		private void updateProgressBar( final String msg, final Long cur, final Long max )
		{
			if ( msg != null && !msg.trim().isEmpty() )
				progressBar.setString( "Building Python environment: " + msg.trim() );
			if ( cur != null || max != null )
				progressBar.setIndeterminate( false );
			if ( max != null )
				progressBar.setMaximum( max.intValue() );
			if ( cur != null )
				progressBar.setValue( cur.intValue() );
		}
	}

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

	/**
	 * Transfers the calibration of an {@link ImgPlus} to an {@link ImagePlus}.
	 * This include units, pixel sizes and frame interval. Y is supposed to be
	 * the same as X, and Z is supposed to have the same unit as X.
	 * 
	 * @param from
	 *            the ImgPlus to copy calibration info from.
	 * @param to
	 *            the ImagePlus to copy to.
	 */
	public static final void transferCalibration( final ImgPlusMetadata from, final ImagePlus to )
	{
		final Calibration tc = to.getCalibration();
		for ( int d = 0; d < from.numDimensions(); d++ )
		{
			if ( from.axis( d ).type().equals( Axes.X ) )
			{
				tc.setXUnit( from.axis( d ).unit() );
				tc.pixelWidth = from.averageScale( d );
				tc.pixelHeight = from.averageScale( d );
				// We suppose X = Y and same units for Z.
				break;
			}
			else if ( from.axis( d ).type().equals( Axes.Z ) )
			{
				tc.pixelDepth = from.averageScale( d );
				break;
			}
			else if ( from.axis( d ).type().equals( Axes.TIME ) )
			{
				tc.setTimeUnit( from.axis( d ).unit() );
				tc.frameInterval = from.averageScale( d );
				break;
			}
		}
	}

	/*
	 * Check if the Image is 3D or 2D
	 */
	public static boolean is3d( final ImagePlus imp )
	{
		return imp.getNSlices() > 1;
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

	public static List< String > getChannelChoices( final ImagePlus imp, final boolean cp3_mode )
	{
		final List< String > channelChoices = new ArrayList<>();
		for ( int i = 1; i <= imp.getNChannels(); i++ )
		{
			channelChoices.add( String.valueOf( i ) );
		}
		channelChoices.add( "None" );
		if ( cp3_mode )
			channelChoices.add( "Average" );
		return channelChoices;
	}

	public static Integer convertChannelChoiceToInt( final String input, final boolean cp3_mode )
	{
		if ( cp3_mode )
			return Objects.equals( input, "None" ) ? -1 : ( Objects.equals( input, "Average" ) ? 0 : ( input == null ? -1 : Integer.parseInt( input ) ) );
		return Objects.equals( input, "None" ) ? -1 : ( input == null ? -1 : Integer.parseInt( input ) - 1 );
	}

	public static void addROIs( final ImagePlus labels )
	{
		addROIs( labels, "r" );
	}

	/**
	 * Creates ImageJ ROIs from a label image, and adds them to the RoiManager.
	 * The ROIs are {@link PolygonRoi}s
	 * 
	 * @param labels
	 *            the label image to create ROIs from.
	 * @param prefix
	 *            the prefix to use for naming the ROIs.
	 */
	public static void addROIs( final ImagePlus labels, final String prefix )
	{
		addROIs( labels, prefix, null );
	}

	public static void addROIs( final ImagePlus labels, final String prefix, final Color color )
	{
		final RoiManager rm = RoiManager.getRoiManager();
		toROIs( labels, prefix, color ).forEach( rm::addRoi );
	}

	/**
	 * Converts a label image into a list of ImageJ ROIs. The ROIs are
	 * {@link PolygonRoi}s.
	 * 
	 * @param labels
	 *            the label image to create ROIs from.
	 * @param prefix
	 *            the prefix to use for naming the ROIs.
	 * @param color
	 *            the color to use for the ROIs. If null, the default color will
	 *            be used.
	 * @return a list of ROIs corresponding to the labels in the input image.
	 */
	public static List< PolygonRoi > toROIs( final ImagePlus labels, final String prefix, final Color color )
	{
		// from
		// https://github.com/ijpb/MorphoLibJ/blob/master/src/main/java/inra/ijpb/plugins/LabelMapToPolygonRois.java

		final ImageProcessor image = labels.getProcessor();

		final int conn = 4;
		final LabelMapToPolygons.VertexLocation loc = LabelMapToPolygons.VertexLocation.CORNER;

		// compute boundaries
		final LabelMapToPolygons tracker = new LabelMapToPolygons( conn, loc );
		final Map< Integer, ArrayList< Polygon2D > > boundaries = tracker.process( image );
		final int nRois = boundaries.values().stream().mapToInt( List::size ).sum();
		final int nDigits = ( int ) Math.ceil( Math.log10( nRois + 1 ) );
		final String pattern = prefix + "_%0" + nDigits + "d";

		final List< PolygonRoi > rois = new ArrayList<>( nRois );
		int index = 1; // Start at 1 to match ImageJ ROI display
		for ( final int label : boundaries.keySet() )
		{
			final ArrayList< Polygon2D > polygons = boundaries.get( label );

			if ( polygons.size() <= 1 && nRois <= 1 )
			{
				final PolygonRoi roi = polygons.get( 0 ).createRoi();
				roi.setName( prefix );
				roi.setStrokeColor( color );
				rois.add( roi );
			}
			else
			{
				for ( final Polygon2D poly : polygons )
				{
					final PolygonRoi roi = poly.createRoi();
					final String name = String.format( pattern, index++ );
					roi.setName( name );
					roi.setStrokeColor( color );
					rois.add( roi );
				}
			}
		}
		return rois;
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
		CUDA_VERSION_MAP.put( "12", "126" );
		CUDA_VERSION_MAP.put( "13", "130" ); 
	}

	/**
	 * Maps a raw CUDA version string to the pixi environment suffix using
	 * {@link #CUDA_VERSION_MAP}.
	 *
	 * @return the mapped suffix, or {@code null} if the version is not recognized.
	 */
	static String mapCudaVersion( final String rawVersion )
	{
		// Only pass the major version (e.g. "12" from "12.6") to the map, as minor versions are not distinguished in the pixi environments.
		final String majorVersion = rawVersion.split( "\\." )[ 0 ];
		return CUDA_VERSION_MAP.get( majorVersion );
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
			if ( imp.getNChannels() > 1 )
			{
				if ( imp.getNFrames() > 1 )
				{
					// XYCT -> TCYX
					return new ImageAxisInfo( null, 1, 0 );
				}
				// XYC -> CYX
				return new ImageAxisInfo( null, 0, null );
			}

			if ( imp.getNFrames() > 1 )
			{
				// XYT -> TYX
				return new ImageAxisInfo( null, null, 0 );
			}
			// XY
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
