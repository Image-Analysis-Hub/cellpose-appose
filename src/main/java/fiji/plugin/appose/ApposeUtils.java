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
import java.util.List;
import java.util.Map;

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
		
		// shift the roi in the crop image size (outputs are cropped images)
		final Rectangle bounds = roi.getBounds();
		roi.translate( -bounds.x, -bounds.y );

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
		// put to the ROI back to original image size
		roi.translate( bounds.x, bounds.y );
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
	 * Creates a list of ImageJ ROIs from a label image and adds them to the ROI
	 * manager. The ROIs are {@link PolygonRoi}s.
	 * 
	 * @param labels
	 *            the label image to create ROIs from. Important: the ROIs are
	 *            created at coordinates relative to the calibration.xOrigin and
	 *            calibration.yOrigin of this label image, so that they are the
	 *            right position if the label image was generated from a crop
	 *            view of the input.
	 * @param prefix
	 *            the prefix to use for naming the ROIs.
	 * @param color
	 *            the color to use for the ROIs. If <code>null</code>, the
	 *            default color will be used.
	 * @param tOrigin
	 *            the time origin for the ROIs. ROIs with frame 1 will be
	 *            displayed at frame tOrigin +1, etc.
	 * @param multipleChannels
	 *            set it to <code>true</code> if the target image has multiple
	 *            channels. Otherwise the ROIs will be displayed on all frames
	 *            of the target image.
	 * @return a list of ROIs corresponding to the labels in the input image.
	 */
	public static void addROIs( final ImagePlus labels, final String prefix, final Color color, final int tOrigin, final boolean multipleChannels )
	{
		final RoiManager rm = RoiManager.getRoiManager();
		toROIs( labels, prefix, color, tOrigin, multipleChannels ).forEach( rm::addRoi );
	}

	/**
	 * Converts a label image into a list of ImageJ ROIs. The ROIs are
	 * {@link PolygonRoi}s.
	 * 
	 * @param labels
	 *            the label image to create ROIs from. Important: the ROIs are
	 *            created at coordinates relative to the calibration.xOrigin and
	 *            calibration.yOrigin of this label image, so that they are the
	 *            right position if the label image was generated from a crop
	 *            view of the input.
	 * @param prefix
	 *            the prefix to use for naming the ROIs.
	 * @param color
	 *            the color to use for the ROIs. If <code>null</code>, the
	 *            default color will be used.
	 * @param tOrigin
	 *            the time origin for the ROIs. ROIs with frame 1 will be
	 *            displayed at frame tOrigin + 1, etc.
	 * @param multipleChannels
	 *            set it to <code>true</code> if the target image has multiple
	 *            channels. Otherwise the ROIs will be displayed on all frames
	 *            of the target image.
	 * @return a list of ROIs corresponding to the labels in the input image.
	 */
	public static List< PolygonRoi > toROIs( final ImagePlus labels, final String prefix, final Color color, final int tOrigin, final boolean multipleChannels )
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
			final String pattern = ( nt > 1 || tOrigin > 0 )
					? prefix + "_t%0" + nDigitsT + "d" + "_%0" + nDigits + "d"
					: prefix + "_%0" + nDigits + "d";

			int index = 1; // Start at 1 to match ImageJ ROI display

			/*
			 * There is some weirdness in ImageJ display of multiple ROIs from
			 * the ROI manager. If the target imp has multiple channels, and if
			 * you assign the ROI channel to 0, then the ROI is properly
			 * displayed on all channels, but only on its frame, as expected.
			 * 
			 * BUT if the imp has only one channel, then the ROI is displayed on
			 * all frames, which is not what we want. The workaround is to
			 * assign the ROI channel to 1.
			 */
			final int targetChannel = multipleChannels
					? 0 // Show ROIs on all channels.
					: 1;
			for ( final int label : boundaries.keySet() )
			{
				final ArrayList< Polygon2D > polygons = boundaries.get( label );

				if ( polygons.size() <= 1 && nRois <= 1 )
				{
					final PolygonRoi roi = polygons.get( 0 ).createRoi();
					roi.translate( labels.getCalibration().xOrigin, labels.getCalibration().yOrigin );
					roi.setName( prefix );
					roi.setStrokeColor( color );
					roi.setPosition( targetChannel, 1, t + tOrigin );
					rois.add( roi );
				}
				else
				{
					for ( final Polygon2D poly : polygons )
					{
						final PolygonRoi roi = poly.createRoi();
						roi.translate( labels.getCalibration().xOrigin, labels.getCalibration().yOrigin );
						
						final String name = ( nt > 1 || tOrigin > 0 )
								? String.format( pattern, t + tOrigin, index++ )
								: String.format( pattern, index++ );
						roi.setPosition( targetChannel, 1, t + tOrigin );
						roi.setName( name );
						roi.setStrokeColor( color );
						rois.add( roi );
					}
				}
			}
		}
		return rois;
	}
}
