/*-
 * #%L
 * Running Cellpose with a Fiji plugin based on Appose.
 * %%
 * Copyright (C) 2026 My Company, Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package fiji.plugin.appose;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.apposed.appose.TaskEvent;
import org.scijava.Context;

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
	 * Forwards Appose task events to a {@link org.scijava.task.Task}, so that
	 * the progress is properly displayed in the UI.
	 * 
	 * @param fijiTask
	 *            the Fiji task to update with Appose task events.
	 * @return a consumer of Appose task events that updates the given task
	 *         accordingly.
	 */
	public static Consumer< TaskEvent > apposeTaskListener( final org.scijava.task.Task fijiTask )
	{
		return e -> {
			if ( e.message != null )
				fijiTask.setStatusMessage( e.message );

			if ( e.current >= 0 )
				fijiTask.setProgressValue( e.current );

			if ( e.maximum >= 0 )
				fijiTask.setProgressMaximum( e.maximum );
		};
	}

	public static class ApposeLogger
	{

		private volatile JDialog progressDialog;

		private volatile JProgressBar progressBar;

		public void close()
		{
			EventQueue.invokeLater( () -> {
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
				}
				if ( msg != null && !msg.trim().isEmpty() )
					progressBar.setString( "Building Python environment: " + msg.trim() );
				if ( cur != null || max != null )
					progressBar.setIndeterminate( false );
				if ( max != null )
					progressBar.setMaximum( max.intValue() );
				if ( cur != null )
					progressBar.setValue( cur.intValue() );
			} );
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
			return Objects.equals( input, "None" ) ? null : ( Objects.equals( input, "Average" ) ? 0 : ( input == null ? null : Integer.parseInt( input ) ) );
		return Objects.equals( input, "None" ) ? null : ( input == null ? null : Integer.parseInt( input ) - 1 );
	}

	public static void addROIs( final ImagePlus labels )
	{
		// from
		// https://github.com/ijpb/MorphoLibJ/blob/master/src/main/java/inra/ijpb/plugins/LabelMapToPolygonRois.java

		final ImageProcessor image = labels.getProcessor();

		final int conn = 4;
		final LabelMapToPolygons.VertexLocation loc = LabelMapToPolygons.VertexLocation.CORNER;
		final String pattern = "r%03d";

		// compute boundaries
		final LabelMapToPolygons tracker = new LabelMapToPolygons( conn, loc );
		final Map< Integer, ArrayList< Polygon2D > > boundaries = tracker.process( image );

		RoiManager rm = RoiManager.getInstance();
		if ( rm == null )
		{
			rm = new RoiManager();
		}
		// populate RoiManager with PolygonRoi
		for ( final int label : boundaries.keySet() )
		{
			final ArrayList< Polygon2D > polygons = boundaries.get( label );
			final String name = String.format( pattern, label );

			if ( polygons.size() == 1 )
			{
				final PolygonRoi roi = polygons.get( 0 ).createRoi();
				roi.setName( name );
				rm.addRoi( roi );
			}
			else
			{
				int index = 0;
				for ( final Polygon2D poly : polygons )
				{
					final PolygonRoi roi = poly.createRoi();
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
