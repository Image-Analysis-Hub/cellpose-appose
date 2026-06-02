/*-
 * #%L
 * Running Cellpose with a Fiji plugin based on Appose.
 * %%
 * Copyright (C) 2026 Appose developpers
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the My Company nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package fiji.plugin.appose;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
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
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cellpose.CellposeOutput;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public class ApposeUtils
{

	/**
	 * A utility to wrap an ImagePlus into an ImgPlus, without too many
	 * warnings.
	 * <p>
	 * If the input ImagePlus has a ROI, the returned ImgPlus will be a view of
	 * the original image, restricted to the bounding box of the ROI in X and Y
	 * (and with min for X and Y set to the min & max of the ROI bounding box).
	 * If the input ImagePlus does not have a ROI, the returned ImgPlus wrap the
	 * full image.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static final < T > ImgPlus< T > rawWraps( final ImagePlus imp )
	{
		final Roi roi = imp.getRoi();
		final ImgPlus< DoubleType > img = ImagePlusAdapter.wrapImgPlus( imp );
		final ImgPlus raw = img;
		if ( roi == null )
			return raw;
		
		// Crop the view to the bounding box of the ROI.
		final Rectangle bounds = roi.getBounds();
		final long min[] = img.minAsLongArray();
		final long max[] = img.maxAsLongArray();
		min[ 0 ] = bounds.x;
		min[ 1 ] = bounds.y;
		max[ 0 ] = bounds.x + bounds.width - 1;
		max[ 1 ] = bounds.y + bounds.height - 1;
		final FinalInterval interval = new FinalInterval( min, max );
		final RandomAccessibleInterval view = Views.interval( raw, interval );

		final Img img2 = ImgView.wrap( view, img.factory() );
		final ImgPlus raw2 = new ImgPlus( img2, raw );
		return raw2;
	}

	/**
	 * Clear pixels outside the ROI in the Cellpose output.
	 */
	public static final < T extends NativeType< T > & IntegerType< T > > void clearOutsideRoi( final CellposeOutput< T > output, final Roi roi )
	{
		if ( roi == null )
			return;

		final RandomAccessibleInterval< T > labels = output.labels;
		final Cursor< T > c = labels.localizingCursor();
		while ( c.hasNext() )
		{
			c.next();
			final int x = c.getIntPosition( 0 );
			final int y = c.getIntPosition( 1 );
			if ( !roi.contains( x, y ) )
				c.get().setZero();
		}

		if ( output.flows != null )
		{
			final RandomAccessibleInterval< UnsignedByteType > flows = output.flows;
			final Cursor< UnsignedByteType > cFlows = flows.localizingCursor();
			while ( cFlows.hasNext() )
			{
				cFlows.next();
				final int x = cFlows.getIntPosition( 0 );
				final int y = cFlows.getIntPosition( 1 );
				if ( !roi.contains( x, y ) )
					cFlows.get().setZero();
			}
		}
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
	 * Check if the Image is 3D or 2D
	 */
	public static boolean is3d( final ImagePlus imp )
	{
		return imp.getNSlices() > 1;
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
		// We don't create ROIs for 3D images.
		if ( labels.getNSlices() > 1 )
			return Collections.emptyList();

		final List< PolygonRoi > rois = new ArrayList<>();
		final int nt = labels.getNFrames();
		final int nDigitsT = ( int ) Math.ceil( Math.log10( nt + 1 ) );

		for ( int t = 1; t <= nt; t++ )
		{
			final ImageProcessor image = labels.getImageStack().getProcessor( t );

			final int conn = 4;
			final LabelMapToPolygons.VertexLocation loc = LabelMapToPolygons.VertexLocation.CORNER;

			// compute boundaries
			final LabelMapToPolygons tracker = new LabelMapToPolygons( conn, loc );
			final Map< Integer, ArrayList< Polygon2D > > boundaries = tracker.process( image );
			final int nRois = boundaries.values().stream().mapToInt( List::size ).sum();
			final int nDigits = ( int ) Math.ceil( Math.log10( nRois + 1 ) );
			final String pattern = ( nt > 1 )
					? prefix + "_t%0 " + nDigitsT + "d" + "_%0" + nDigits + "d"
					: prefix + "_%0" + nDigits + "d";

			int index = 1; // Start at 1 to match ImageJ ROI display
			for ( final int label : boundaries.keySet() )
			{
				final ArrayList< Polygon2D > polygons = boundaries.get( label );

				if ( polygons.size() <= 1 && nRois <= 1 )
				{
					final PolygonRoi roi = polygons.get( 0 ).createRoi();
					roi.translate( labels.getCalibration().xOrigin, labels.getCalibration().yOrigin );
					roi.setName( prefix );
					roi.setStrokeColor( color );
					rois.add( roi );
				}
				else
				{
					for ( final Polygon2D poly : polygons )
					{
						final PolygonRoi roi = poly.createRoi();
						roi.translate( labels.getCalibration().xOrigin, labels.getCalibration().yOrigin );
						final String name = ( nt > 1 )
								? String.format( pattern, t, index++ )
								: String.format( pattern, index++ );
						roi.setPosition( t );
						roi.setName( name );
						roi.setStrokeColor( color );
						rois.add( roi );
					}
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
	 * Checks if CUDA is available on the system by trying to execute {@code nvidia-smi}.
	 * This method returns {@code false} on macOS, as CUDA is not supported on that platform.
	 * @return
	 */
	public static Boolean asCUDA()
	{
		if ( getOperatingSystem() == OperatingSystem.MACOS )
			return false;
		try
		{
			// try to run nvidia-smi to check if it is available
			final ProcessBuilder pb = new ProcessBuilder( "nvidia-smi" );
			pb.redirectErrorStream( true );
			final Process process = pb.start();
			process.waitFor();
			return process.exitValue() == 0;
		}
		catch ( final IOException | InterruptedException e )
		{
			return false;
		}
	}

	/**
	 * Returns the CUDA version available on the system by querying
	 * {@code nvidia-smi}, or {@code null} if CUDA is not available or the OS is
	 * macOS. The returned value is already mapped to the pixi environment
	 * suffix (e.g. {@code "126"}, {@code "130"}).
	 * <p>
	 * {@code nvidia-smi} is preferred over {@code nvcc} because it reflects the
	 * driver-supported CUDA version and is present on any system with a GPU
	 * driver installed, even without the full CUDA toolkit.
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
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader( process.getInputStream() ) ))
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
	 * pixi environment suffix. Only versions listed here are supported; any
	 * other version returns {@code null}.
	 */
	private static final Map< String, String > CUDA_VERSION_MAP;
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
	 * @return the mapped suffix, or {@code null} if the version is not
	 *         recognized.
	 */
	private static String mapCudaVersion( final String rawVersion )
	{
		// Only pass the major version (e.g. "12" from "12.6") to the map, as
		// minor versions are not distinguished in the pixi environments.
		final String majorVersion = rawVersion.split( "\\." )[ 0 ];
		return CUDA_VERSION_MAP.get( majorVersion );
	}
}
