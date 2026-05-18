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

import static fiji.plugin.appose.ApposeUtils.getBestTorchConfig;
import static fiji.plugin.appose.ApposeUtils.outputToImgPlus;
import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static fiji.plugin.appose.ApposeUtils.transferCalibration;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import fiji.plugin.appose.cellpose.cp4.Cellpose4Parameters;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.util.Util;

/**
 * Static calls to Cellpose-3 or Cellpose-SAM.
 */
public class Cellpose
{

	/**
	 * Core method to run Cellpose 3 or Cellpose-SAM, depending on the
	 * specification of the script and environment to use. To be used by other
	 * methods in this class.
	 * 
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param img
	 *            the input image.
	 * @param params
	 *            the parameters to run Cellpose with.
	 * @param pythonScriptPath
	 *            the path to the Python script to run (e.g. "/cp3.py" or
	 *            "/cp4.py").
	 * @param envName
	 *            the name of the Python environment to create and use (e.g.
	 *            "cp3" or "cp4").
	 * @return a list containing the label image, and optionally the flows
	 *         image. If flows are not computed, the list will contain only the
	 *         label image.
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws InterruptedException
	 *             if the thread is interrupted while waiting for the Python
	 *             script to finish.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 */
	private static < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > CellposeOutput< R > run(
			final ImgPlus< T > input,
			final CellposeParameters params,
			final String pythonScriptPath,
			final String envName ) throws BuildException, IOException, InterruptedException, TaskException
	{
		try (final CellposeRunner runner = new CellposeRunner( params, pythonScriptPath, envName ))
		{
			runner.init();

			// Do we have a 5D image? If yes we process time by time.
			final int tAxis = input.dimensionIndex( Axes.TIME );
			final int nt = tAxis >= 0 ? ( int ) input.dimension( tAxis ) : 1;
			final int zAxis = input.dimensionIndex( Axes.Z );
			final int nz = zAxis >= 0 ? ( int ) input.dimension( zAxis ) : 1;
			final int cAxis = input.dimensionIndex( Axes.CHANNEL );

			if ( nt > 1 && nz > 1 )
			{
				/*
				 * One issue is that we don't know in advance what the type of
				 * the labels output is going to be. It can be uint32 or uint64,
				 * the latter happening if there are more that 65k labels in one
				 * time-point. And this can happen at any time-point.
				 * 
				 * For now, we are optimistic, and assume it is only uint16 for
				 * 5D use cases. Other use cases are unaffected.
				 */

				// Placeholder for labels output.
				long[] ldims = input.dimensionsAsLongArray();
				if ( cAxis >= 0 )
				{
					ldims[ cAxis ] = 1; // only 1 channel in the labels output
				}
				else
				{
					// If there is no channel axis, we add one.
					ldims = new long[] { ldims[ 0 ], ldims[ 1 ], 1, ldims[ 2 ], ldims[ 3 ] };
				}
				final Dimensions labelsDim = FinalDimensions.wrap( ldims );
				final Img< UnsignedIntType > outputLabels = Util.getArrayOrCellImgFactory( labelsDim, new UnsignedIntType() ).create( ldims );
				final ImgPlus< UnsignedIntType > outputLabelsImgPlus = outputToImgPlus( outputLabels, input );

				// Placeholder for flows output if needed.
				final ImgPlus< UnsignedByteType > outputFlowsImgPlus;
				if ( params.computeFlows )
				{
					long[] fdims = input.dimensionsAsLongArray();
					if ( cAxis >= 0 )
					{
						fdims[ cAxis ] = 3; // 3 channels in the flows output
					}
					else
					{
						// If there is no channel axis, we add one.
						fdims = new long[] { fdims[ 0 ], fdims[ 1 ], 3, fdims[ 2 ], fdims[ 3 ] };
					}
					final Img< UnsignedByteType > outputFlows = Util.getArrayOrCellImgFactory( labelsDim, new UnsignedByteType() ).create( fdims );
					outputFlowsImgPlus = outputToImgPlus( outputFlows, input );
				}
				else
				{
					outputFlowsImgPlus = null;
				}

				// Process time point by time point.
				for ( int t = 0; t < nt; t++ )
				{
					// Input reslice.
					final ImgPlus< T > inputTp = ImgPlusViews.hyperSlice( input, tAxis, t );

					// Labels output reslice.
					final ImgPlus< UnsignedIntType > outputLabelsImgPlusTp = ImgPlusViews.hyperSlice( outputLabelsImgPlus, tAxis, t );

					// Flows output reslice.
					final ImgPlus< UnsignedByteType > outputFlowsImgPlusTp;
					if ( params.computeFlows )
						outputFlowsImgPlusTp = ImgPlusViews.hyperSlice( outputFlowsImgPlus, tAxis, t );
					else
						outputFlowsImgPlusTp = null;

					// In a CellposeOutput.
					final CellposeOutput< UnsignedIntType > outputTp = new CellposeOutput<>( outputLabelsImgPlusTp, outputFlowsImgPlusTp );

					// Exec and write output in the right place.
					runner.run( inputTp, outputTp );
				}

				// Return all time-points.
				@SuppressWarnings( "unchecked" )
				final CellposeOutput< R > out = ( CellposeOutput< R > ) new CellposeOutput< UnsignedIntType >( outputLabelsImgPlus, outputFlowsImgPlus );
				return out;
			}
			else
			{
				// Otherwise process in one go.
				return runner.run( input, null );
			}
		}
	}

	/**
	 * Run Cellpose 3 with the given parameters on the given image, and return
	 * the resulting label image, and optionally the flows.
	 * 
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param img
	 *            the input image.
	 * @param params
	 *            the parameters to run Cellpose with.
	 * @return a {@link CellposeOutput} object containing the label image, and
	 *         optionally the flows image.
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 */
	public static < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > CellposeOutput< R > cellpose3( final ImgPlus< T > img, final Cellpose3Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final String envName = "cp3" + getBestTorchConfig();
		final String pythonScriptPath = "/cp3.py";
		return run( img, params, pythonScriptPath, envName );
	}

	/**
	 * Run Cellpose 3 on the given image with the given parameters, and return
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
	public static ImagePlus[] cellpose3( final ImagePlus imp, final Cellpose3Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final ImgPlus img = rawWraps( imp );
		final CellposeOutput outputs = cellpose3( img, params );
		final ImagePlus[] imps = toImp( outputs );
		for ( final ImagePlus out : imps )
			ApposeUtils.transferCalibration( imp, out );
		imps[ 0 ].setTitle( imp.getTitle() + "_Cellpose-3" );
		if ( params.computeFlows )
			imps[ 1 ].setTitle( imp.getTitle() + "_flows_Cellpose-3" );
		return imps;
	}

	/**
	 * Run Cellpose-SAM with the given parameters on the given image, and return
	 * the resulting label image, and optionally the flows.
	 * 
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param img
	 *            the input image.
	 * @param params
	 *            the parameters to run Cellpose with.
	 * @return a {@link CellposeOutput} object containing the label image, and
	 *         optionally the flows image.
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 */
	public static < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > CellposeOutput< R > cellpose4( final ImgPlus< T > img, final Cellpose4Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final String envName = "cp4" + getBestTorchConfig();
		final String pythonScriptPath = "/cp4.py";
		return run( img, params, pythonScriptPath, envName );
	}

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
	public static ImagePlus[] cellpose4( final ImagePlus imp, final Cellpose4Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final ImgPlus img = rawWraps( imp );
		final CellposeOutput outputs = cellpose4( img, params );
		final ImagePlus[] imps = toImp( outputs );
		for ( final ImagePlus out : imps )
			ApposeUtils.transferCalibration( imp, out );
		imps[ 0 ].setTitle( imp.getTitle() + "_Cellpose-SAM" );
		if ( params.computeFlows )
			imps[ 1 ].setTitle( imp.getTitle() + "_flows_Cellpose-SAM" );
		return imps;
	}

	private static < R extends IntegerType< R > & NativeType< R > > ImagePlus[] toImp( final CellposeOutput< R > outputs )
	{
		final ImgPlus< R > output = outputs.labels;
		final ImagePlus labels = ImageJFunctions.wrap( output, "labels" );
		final StackStatistics stats = new StackStatistics( labels );
		labels.setDisplayRange( stats.min, stats.max );
		useGlasbeyDarkLUT( labels );
		transferCalibration( output, labels );
		if ( outputs.flows != null )
		{
			final ImgPlus< UnsignedByteType > flows = outputs.flows;
			ImagePlus flowsImp = ImageJFunctions.wrap( flows, "flows" );
			flowsImp.getProcessor().resetMinAndMax();
			flowsImp = new CompositeImage( flowsImp );
			flowsImp.setDisplayMode( CompositeImage.COMPOSITE );
			transferCalibration( flows, flowsImp );
			return new ImagePlus[] { labels, flowsImp };
		}
		return new ImagePlus[] { labels };
	}
}
