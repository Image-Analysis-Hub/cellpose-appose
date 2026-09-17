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

import static fiji.plugin.appose.ApposeUtils.getAxisInfo;
import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static org.scijava.ui.config.fiji.PostProcessUtils.clearOutsideRoi;
import static org.scijava.ui.config.fiji.PostProcessUtils.transferCalibration;
import static org.scijava.ui.config.fiji.PostProcessUtils.useGlasbeyDarkLUT;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
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
		// Execute Cellpose-SAM.
		final ImgPlus input = rawWraps( imp );
		final AxisInfo inputAxes = getAxisInfo( input );
		final CellposeOutput outputs = net.imglib2.cellpose.Cellpose.cellpose4( input, inputAxes, params, listener );

		// Post-processing: transfer calibration and clear outside ROI.
		final ImagePlus[] imps = toImp( outputs );
		for ( final ImagePlus out : imps )
		{
			Roi inputRoi = imp.getRoi();
			if ( inputRoi != null )
				inputRoi = ( Roi ) inputRoi.clone();
			transferCalibration( imp, out, inputRoi );
			clearOutsideRoi( out, inputRoi );
		}

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
		// Execute Cellpose-3.
		final ImgPlus input = rawWraps( imp );
		final AxisInfo inputAxes = getAxisInfo( input );
		final CellposeOutput outputs = net.imglib2.cellpose.Cellpose.cellpose3( input, inputAxes, params, listener );

		// Post-processing: transfer calibration and clear outside ROI.
		final ImagePlus[] imps = toImp( outputs );
		for ( final ImagePlus out : imps )
		{
			Roi inputRoi = imp.getRoi();
			if ( inputRoi != null )
				inputRoi = ( Roi ) inputRoi.clone();
			transferCalibration( imp, out, inputRoi );
			clearOutsideRoi( out, inputRoi );
		}
		imps[ 0 ].setTitle( imp.getTitle() + "_Cellpose-3" );
		if ( params.computeFlows )
			imps[ 1 ].setTitle( imp.getTitle() + "_flows_Cellpose-3" );
		return imps;
	}

	public static < R extends IntegerType< R > & NativeType< R > > ImagePlus[] toImp( final CellposeOutput< R > outputs )
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
}
