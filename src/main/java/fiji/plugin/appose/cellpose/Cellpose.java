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
import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static fiji.plugin.appose.ApposeUtils.transferCalibration;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.ApposeUtils.ApposeLogger;
import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import fiji.plugin.appose.cellpose.cp4.Cellpose4Parameters;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

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
			final ImgPlus< T > img,
			final CellposeParameters params,
			final String pythonScriptPath,
			final String envName ) throws BuildException, IOException, InterruptedException, TaskException
	{
		// Inputs.
		final Map< String, Object > inputs = params.toApposeMap( img );

		// Python env. specifications.
		final String cellposeEnv = pixiEnv();

		// Create Python env.
		final ApposeLogger logger = new ApposeUtils.ApposeLogger();
		final Environment env = Appose
				.pixi()
				.content( cellposeEnv )
				.subscribeProgress( logger::showProgress )
				.subscribeOutput( logger::showProgress )
				.subscribeError( logger::showProgress )
				.environment( envName )
				.build();
		logger.close();

		// Python scripts and service.
		final String utilsScript = IOUtils.toString( Cellpose.class.getResource( "/cp_utils.py" ), StandardCharsets.UTF_8 );
		final String cellposeScript = IOUtils.toString( Cellpose.class.getResource( pythonScriptPath ), StandardCharsets.UTF_8 );
		try (Service python = env.python().init( utilsScript ))
		{
			// The Python task.
			final Task task = python.task( cellposeScript, inputs );

			// Start the script, and return to Java immediately.
			IJ.showStatus( "Starting Cellpose-Appose task..." );
			final long start = System.currentTimeMillis();
			// To catch update message from the python script
			task.listen( ApposeUtils.ijTaskListener() );
			task.start();
			// Wait for task completion.
			task.waitFor();

			// Verify that it worked.
			if ( task.status != TaskStatus.COMPLETE )
				throw new RuntimeException( "Python script failed with error: " + task.error );

			// Benchmark.
			final long end = System.currentTimeMillis();
			IJ.showStatus( "Cellpose finished in " + ( end - start ) / 1000. + " s" );

			// Unwrap and process outputs.
			final NDArray labelsArr = ( NDArray ) task.outputs.get( "labels" );
			final Img< R > labels = new ShmImg<>( labelsArr );
			final ImgPlus< R > labelsImgPlus = outputToImgPlus( labels, img );

			if ( params.computeFlows )
			{
				final NDArray flowsArr = ( NDArray ) task.outputs.get( "flows" );
				final Img< UnsignedByteType > flows = new ShmImg<>( flowsArr );
				final ImgPlus< UnsignedByteType > flowsImgPlus = outputToImgPlus( flows, img );
				return new CellposeOutput< R >( labelsImgPlus, flowsImgPlus );
			}
			return new CellposeOutput<>( labelsImgPlus );
		}
	}

	/**
	 * Convert (wrap) the Img output of Cellpose-Appose to an ImgPlus with
	 * metadata. We simply copy the input metadata, skipping the channel axis,
	 * and suppose all the output axes are in the same order that of the input.
	 * <p>
	 * The contract is that the output <code>img</code> returned by the cp3.py
	 * script is always a XYCZT image. The input might not be have all these
	 * dimensions and we want to return an output {@link ImgPlus} with
	 * dimensions that match the input.
	 * 
	 * @param <R>
	 *            the type of pixel.
	 * @param img
	 *            the output image to convert.
	 * @param metadata
	 *            the input image to read metadata from.
	 * @return
	 */
	private static < R extends Type< R >, T > ImgPlus< R > outputToImgPlus( final Img< R > img, final ImgPlus< T > metadata )
	{
		assert img.numDimensions() == 5;

		// Drop only the singleton dimensions
		final List< Integer > keptDims = new ArrayList<>( 5 );
		for ( int d = 0; d < 5; d++ )
		{
			if ( img.dimension( d ) > 1 )
				keptDims.add( d );
		}

		final RandomAccessibleInterval< R > view = Views.dropSingletonDimensions( img );
		final Img< R > wrapped = ImgView.wrap( view, img.factory() );

		// We expect the Python code to always return the image in this order.
		final CalibratedAxis[] allAxes = new CalibratedAxis[] {
				new DefaultLinearAxis( Axes.X ),
				new DefaultLinearAxis( Axes.Y ),
				new DefaultLinearAxis( Axes.CHANNEL ),
				new DefaultLinearAxis( Axes.Z ),
				new DefaultLinearAxis( Axes.TIME )
		};

		final List< CalibratedAxis > newAxes = new ArrayList<>();
		for ( final int d : keptDims )
			newAxes.add( allAxes[ d ] );

		// Copy name and calibration from original metadata if available
		final String name = metadata.getName();
		final ImgPlus< R > result = new ImgPlus<>( wrapped, name,
				newAxes.toArray( new CalibratedAxis[ 0 ] ) );

		// Copy scales/units from metadata for matching axes
		for ( int d = 0; d < newAxes.size(); d++ )
		{
			final CalibratedAxis axis = newAxes.get( d );
			for ( int md = 0; md < metadata.numDimensions(); md++ )
			{
				if ( axis.type().equals( metadata.axis( md ).type() ) )
				{
					result.setAxis( metadata.axis( md ), d );
					break;
				}
			}
		}

		return result;
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

	/**
	 * Returns the content of the pixi.toml file to build the environment return
	 * throws IOException
	 */
	public static String pixiEnv() throws IOException
	{
		final URL pixiFile = Cellpose.class.getResource( "/pixi.toml" );
		final String env = IOUtils.toString( pixiFile, StandardCharsets.UTF_8 );
		return env;
	}
}
