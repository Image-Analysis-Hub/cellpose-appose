package fiji.plugin.appose.cellpose.cp3;

import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static fiji.plugin.appose.ApposeUtils.transferCalibration;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;
import static fiji.plugin.appose.cellpose.CellposeOptions.handleTorchBackend;

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
import org.scijava.prefs.PrefService;

import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.ApposeUtils.ApposeLogger;
import ij.IJ;
import ij.ImagePlus;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Static calls to Cellpose 3.
 */
public class Cellpose3
{

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
	public static < T extends RealType< T > & NativeType< T > > List< Img< T > > run( final ImgPlus< T > img, final Cellpose3Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
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
				.environment( "cp3" )
				.build();
		logger.close();

		// Python scripts and service.
		final String utilsScript = IOUtils.toString( Cellpose3.class.getResource( "/cp_utils.py" ), StandardCharsets.UTF_8 );
		final String cp3Script = IOUtils.toString( Cellpose3.class.getResource( "/cp3.py" ), StandardCharsets.UTF_8 );
		try (Service python = env.python().init( utilsScript ))
		{
			// The Python task.
			final Task task = python.task( cp3Script, inputs );

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
			if ( params.computeFlows() )
			{
				final NDArray flowsArr = ( NDArray ) task.outputs.get( "flows" );
				final Img< T > flows = new ShmImg<>( flowsArr );
				return Arrays.asList( output, flows );
			}
			return Collections.singletonList( output );
		}
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
	public static ImagePlus[] run( final ImagePlus imp, final Cellpose3Parameters params ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final ImgPlus img = rawWraps( imp );
		final List< Img< ? > > outputs = run( img, params );

		final Img output = outputs.get( 0 );
		final ImagePlus labels = ImageJFunctions.wrap( output, "labels" );
		final StackStatistics stats = new StackStatistics( labels );
		labels.setDisplayRange( stats.min, stats.max );
		useGlasbeyDarkLUT( labels );
		transferCalibration( img, labels );

		if ( params.computeFlows() )
		{
			final Img flows = outputs.get( 1 );
			final ImagePlus flowsImp = ImageJFunctions.wrap( flows, "flows" );
			flowsImp.getProcessor().resetMinAndMax();
			transferCalibration( imp, flowsImp );
			return new ImagePlus[] { labels, flowsImp };
		}
		return new ImagePlus[] { labels };
	}

	private static String pixiEnv() throws IOException
	{
		final URL pixiFile = Cellpose3.class.getResource( "/pixi.toml" );
		final String env = IOUtils.toString( pixiFile, StandardCharsets.UTF_8 );
		// Check if should change some module version in the pixi string
		final PrefService prefService = ApposeUtils.getContext().getService( PrefService.class );
		return handleTorchBackend( prefService, env );
	}
}
