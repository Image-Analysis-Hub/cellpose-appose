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
package fiji.plugin.appose.cellpose;

import static fiji.plugin.appose.ApposeUtils.clearOutsideRoi;
import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.AxisInfo;
import net.imglib2.cellpose.Cellpose3Parameters;
import net.imglib2.cellpose.Cellpose4Parameters;
import net.imglib2.cellpose.CellposeOutput;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Static calls to Cellpose-3 or Cellpose-SAM.
 */
public class Cellpose
{


	/**
	 * Run Cellpose-SAM on the given image with the given parameters, and return
	 * the resulting label image, and optionally the flows as ImagePlus.
	 * 
	 * @param imp
	 *            the input image.
	 * @param params
	 *            the parameters to run Cellpose with.
	 * @return an array containing the resulting label image, and optionally the
	 *         flows image.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 * @throws InterruptedException
	 *             if the thread is interrupted while waiting for the Python
	 *             script to finish.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static ImagePlus[] cellpose4( final ImagePlus imp,
			final Cellpose4Parameters params,
			final ApposeTaskListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		Roi initialRoi = imp.getRoi();
		if ( initialRoi != null )
			initialRoi = ( Roi ) initialRoi.clone();
		final ImgPlus input = rawWraps( imp );
		final AxisInfo inputAxes = getAxisInfo( input );
		final CellposeOutput outputs = net.imglib2.cellpose.Cellpose.cellpose4( input, inputAxes, params, listener );
		clearOutsideRoi( outputs, initialRoi );

		final ImagePlus[] imps = toImp( outputs );
		for ( final ImagePlus out : imps )
			transferCalibration( imp, out, initialRoi );
		imps[ 0 ].setTitle( imp.getTitle() + "_Cellpose-SAM" );
		if ( params.computeFlows )
			imps[ 1 ].setTitle( imp.getTitle() + "_flows_Cellpose-SAM" );
		return imps;
	}

	/**
	 * Run Cellpose 3 on the given image with the given parameters, and return
	 * the resulting label image, and optionally the flows as ImagePlus.
	 * 
	 * @param imp
	 *            the input image.
	 * @param params
	 *            the parameters to run Cellpose with.
	 * @param listener
	 *            the listener to report progress and messages to.
	 * @return an array containing the resulting label image, and optionally the
	 *         flows image.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 * @throws InterruptedException
	 *             if the thread is interrupted while waiting for the Python
	 *             script to finish.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static ImagePlus[] cellpose3(
			final ImagePlus imp,
			final Cellpose3Parameters params,
			final ApposeTaskListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		Roi initialRoi = imp.getRoi();
		if ( initialRoi != null )
			initialRoi = ( Roi ) initialRoi.clone();
		final ImgPlus input = rawWraps( imp );
		final AxisInfo inputAxes = getAxisInfo( input );
		final CellposeOutput outputs = net.imglib2.cellpose.Cellpose.cellpose3( input, inputAxes, params, listener );
		clearOutsideRoi( outputs, initialRoi );

		final ImagePlus[] imps = toImp( outputs );
		for ( final ImagePlus out : imps )
			transferCalibration( imp, out, initialRoi );
		imps[ 0 ].setTitle( imp.getTitle() + "_Cellpose-3" );
		if ( params.computeFlows )
			imps[ 1 ].setTitle( imp.getTitle() + "_flows_Cellpose-3" );
		return imps;
	}

	private static AxisInfo getAxisInfo( final ImgPlus< ? > img )
	{
		final int x = img.dimensionIndex( Axes.X );
		final int y = img.dimensionIndex( Axes.Y );
		final int c = img.dimensionIndex( Axes.CHANNEL );
		final int z = img.dimensionIndex( Axes.Z );
		final int t = img.dimensionIndex( Axes.TIME );
		return new AxisInfo( x, y, c, z, t );
	}

	private static < R extends IntegerType< R > & NativeType< R > > ImagePlus[] toImp( final CellposeOutput< R > outputs )
	{
		final RandomAccessibleInterval< R > labels = outputs.labels;
		final ImagePlus labelsImp = ImageJFunctions.wrap( labels, "labels" );

		// Set dimensionality. We assume output are always XYCZT.
		final AxisInfo axesLabels = outputs.axesLabels;
		final int nC = ( int ) axesLabels.nChannels( labels );
		final int nZ = ( int ) axesLabels.nZ( labels );
		final int nT = ( int ) axesLabels.nTimePoints( labels );
		labelsImp.setDimensions( nC, nZ, nT );
		labelsImp.getCalibration().xOrigin = labels.min( 0 );
		labelsImp.getCalibration().yOrigin = labels.min( 1 );

		// Set display range and LUT.
		final StackStatistics stats = new StackStatistics( labelsImp );
		labelsImp.setDisplayRange( stats.min, stats.max );
		useGlasbeyDarkLUT( labelsImp );

		// Deal with the flows.
		if ( outputs.flows != null )
		{
			final RandomAccessibleInterval< UnsignedByteType > flows = outputs.flows;
			ImagePlus flowsImp = ImageJFunctions.wrap( flows, "flows" );

			final AxisInfo axesFlows = outputs.axesFlows;
			final int nCFlows = ( int ) axesFlows.nChannels( flows );
			final int nZFlows = ( int ) axesFlows.nZ( flows );
			final int nTFlows = ( int ) axesFlows.nTimePoints( flows );
			flowsImp.setDimensions( nCFlows, nZFlows, nTFlows );
			flowsImp.getCalibration().xOrigin = labels.min( 0 );
			flowsImp.getCalibration().yOrigin = labels.min( 1 );
			flowsImp.getProcessor().resetMinAndMax();
			flowsImp = new CompositeImage( flowsImp );
			flowsImp.setDisplayMode( CompositeImage.COMPOSITE );
			return new ImagePlus[] { labelsImp, flowsImp };
		}
		return new ImagePlus[] { labelsImp };
	}

	/**
	 * Transfers the calibration of an {@link ImagePlus} to another one,
	 * generated from a capture of the first one. Also is the specified ROI is
	 * not null, it will set the origin of the target image to the top-left
	 * corner of the bounding box of the ROI.
	 *
	 * @param from
	 *            the imp to copy from.
	 * @param to
	 *            the imp to copy to.
	 * @param initialRoi
	 */
	private static final void transferCalibration( final ImagePlus from, final ImagePlus to, final Roi initialRoi )
	{
		final Calibration fc = from.getCalibration();
		final Calibration tc = to.getCalibration();

		tc.setUnit( fc.getUnit() );
		tc.setTimeUnit( fc.getTimeUnit() );
		tc.frameInterval = fc.frameInterval;

		tc.pixelWidth = fc.pixelWidth;
		tc.pixelHeight = fc.pixelHeight;
		tc.pixelDepth = fc.pixelDepth;

		if ( initialRoi != null )
		{
			tc.xOrigin = fc.xOrigin + Math.max(0, initialRoi.getBounds().x); // handles case where ROI leaks outside the image
			tc.yOrigin = fc.yOrigin + Math.max(0, initialRoi.getBounds().y);
		}
		else
		{
			tc.xOrigin = fc.xOrigin;
			tc.yOrigin = fc.yOrigin;
		}
	}
}
