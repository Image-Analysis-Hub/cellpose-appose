package fiji.plugin.appose.cellpose;

import static fiji.plugin.appose.ApposeUtils.outputToImgPlus;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import ij.IJ;
import net.imagej.ImgPlus;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.ImgUtil;

/**
 * Specialized class that runs Cellpose. This class exists so that we can write
 * results in a pre-allocated output data structure.
 * <p>
 * Why this class? Normally we can run Cellpose on up to 3D or 2D+T images, with
 * or without C. As soon as we have 3D images over time as input, Cellpose will
 * crash. One solution is to have Cellpose process one time-point after another,
 * writing the results of processing one time-point in a pre-allocated output
 * data structure. This class is made to support this approach.
 * 
 */
class CellposeRunner implements AutoCloseable
{

	private final String envName;

	private final CellposeParameters params;

	private final String pythonScriptPath;

	private Service python;

	private String cellposeScript;

	public CellposeRunner( final CellposeParameters params,
			final String pythonScriptPath,
			final String envName )
	{
		this.params = params;
		this.pythonScriptPath = pythonScriptPath;
		this.envName = envName;
	}

	/**
	 * Runs Cellpose on the given input image, and writes the output to the
	 * given output object. If the output object is <code>null</code>, a new one
	 * will be created and returned.
	 * 
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param <R>
	 *            the pixel type of the output labels image. Can be either
	 *            UnsignedIntType or UnsignedLongType, depending on the expected
	 *            number of cells.
	 * @param input
	 *            the input image.
	 * @param output
	 *            the output object to write results to. If <code>null</code>, a
	 *            new one will be created and returned.
	 * @return the output of Cellpose, containing the labels and possibly flows.
	 *         If the output object was provided, it will be returned after
	 *         being updated with the new results.
	 * @throws InterruptedException
	 *             if the thread is interrupted while waiting for the Python
	 *             script to finish.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 */
	public < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > CellposeOutput< R > run(
			final ImgPlus< T > input,
			final CellposeOutput< R > output ) throws InterruptedException, TaskException
	{
		// Inputs.
		final Map< String, Object > inputs = params.toApposeMap( input );

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
		ImgPlus< R > labelsImgPlus = outputToImgPlus( labels, input );

		if ( params.computeFlows )
		{
			final NDArray flowsArr = ( NDArray ) task.outputs.get( "flows" );
			final Img< UnsignedByteType > flows = new ShmImg<>( flowsArr );
			ImgPlus< UnsignedByteType > flowsImgPlus = outputToImgPlus( flows, input );
			// Drop the time singleton dimension for writing into output.

			if ( output != null )
			{
				// Drop the time singleton dimension for writing into output.
				labelsImgPlus = ImgPlusViews.hyperSlice( labelsImgPlus, 4, 0 );
				flowsImgPlus = ImgPlusViews.hyperSlice( flowsImgPlus, 4, 0 );

				ImgUtil.copy( labelsImgPlus, output.labels );
				ImgUtil.copy( flowsImgPlus, output.flows );
				return output;
			}
			return new CellposeOutput<>( labelsImgPlus, flowsImgPlus );
		}

		if ( output != null )
		{
			// Drop the time singleton dimension for writing into output.
			labelsImgPlus = ImgPlusViews.hyperSlice( labelsImgPlus, 4, 0 );

			// Write the new labels to the provided output object.
			ImgUtil.copy( labelsImgPlus, output.labels );
			return output;
		}
		return new CellposeOutput<>( labelsImgPlus );

	}

	/**
	 * Initializes the Cellpose runner. Must be called before calling run.
	 * 
	 * @throws IOException
	 *             if there is an error reading the pixi.toml file.
	 * @throws BuildException
	 *             if there is an error building the environment.
	 */
	public void init() throws IOException, BuildException
	{
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
		final String utilsScript = IOUtils.toString( Cellpose.class.getResource( "/cp_utils.py" ), StandardCharsets.UTF_8 );
		this.python = env.python().init( utilsScript );

		// The script.
		this.cellposeScript = IOUtils.toString( Cellpose.class.getResource( pythonScriptPath ), StandardCharsets.UTF_8 );
	}

	@Override
	public void close()
	{
		python.close();
	}

	/**
	 * Returns the content of the pixi.toml file to build the environment return
	 * throws IOException
	 */
	public static String pixiEnv() throws IOException
	{
		final URL pixiFile = CellposeRunner.class.getResource( "/pixi.toml" );
		final String env = IOUtils.toString( pixiFile, StandardCharsets.UTF_8 );
		return env;
	}
}
