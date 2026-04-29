package fiji.plugin.appose.cellpose;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
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
import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static fiji.plugin.appose.ApposeUtils.transferCalibration;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;
import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import fiji.plugin.appose.cellpose.cp4.Cellpose4Parameters;
import ij.IJ;
import ij.ImagePlus;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imagej.ImgPlusMetadata;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

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
	private static < T extends RealType< T > & NativeType< T > > List< Img< T > > run(
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
			final NDArray maskArr = ( NDArray ) task.outputs.get( "labels" );
			final Img< T > output = new ShmImg<>( maskArr );
			if ( params.computeFlows )
			{
				final NDArray flowsArr = ( NDArray ) task.outputs.get( "flows" );
				final Img< T > flows = new ShmImg<>( flowsArr );
				return Arrays.asList( output, flows );
			}
			return Collections.singletonList( output );
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
	 * @return a list containing the label image, and optionally the flows
	 *         image. If flows are not computed, the list will contain only the
	 *         label image.
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 */
	public static < T extends RealType< T > & NativeType< T > > List< Img< T > > cellpose3( final ImgPlus< T > img, final Cellpose3Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final String envName = "cp3";
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
		final List< Img< ? > > outputs = cellpose3( img, params );
		final ImagePlus[] imps = toImp( outputs, img, params.computeFlows );
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
	 * @return a list containing the label image, and optionally the flows
	 *         image. If flows are not computed, the list will contain only the
	 *         label image.
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 */
	public static < T extends RealType< T > & NativeType< T > > List< Img< T > > cellpose4( final ImgPlus< T > img, final Cellpose4Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final String envName = "cp4";
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
		final List< Img< ? > > outputs = cellpose4( img, params );
		final ImagePlus[] imps = toImp( outputs, img, params.computeFlows );
		imps[ 0 ].setTitle( imp.getTitle() + "_Cellpose-SAM" );
		if ( params.computeFlows )
			imps[ 1 ].setTitle( imp.getTitle() + "_flows_Cellpose-SAM" );
		return imps;
	}

	private static ImagePlus[] toImp( final List< Img< ? > > outputs, final ImgPlusMetadata metadata, final boolean computeFlows )
	{
		@SuppressWarnings( "rawtypes" )
		final Img output = outputs.get( 0 );
		@SuppressWarnings( "unchecked" )
		final ImagePlus labels = ImageJFunctions.wrap( output, "labels" );
		final StackStatistics stats = new StackStatistics( labels );
		labels.setDisplayRange( stats.min, stats.max );
		useGlasbeyDarkLUT( labels );
		transferCalibration( metadata, labels );
		if ( computeFlows )
		{
			@SuppressWarnings( "rawtypes" )
			final Img flows = outputs.get( 1 );
			@SuppressWarnings( "unchecked" )
			final ImagePlus flowsImp = ImageJFunctions.wrap( flows, "flows" );
			flowsImp.getProcessor().resetMinAndMax();
			transferCalibration( metadata, flowsImp );
			return new ImagePlus[] { labels, flowsImp };
		}
		return new ImagePlus[] { labels };
	}

	private static String pixiEnv() throws IOException
	{
		final URL pixiFile = Cellpose.class.getResource( "/pixi.toml" );
		final String env = IOUtils.toString( pixiFile, StandardCharsets.UTF_8 );
		return env;
	}
}
