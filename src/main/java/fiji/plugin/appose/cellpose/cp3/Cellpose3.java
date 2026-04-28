package fiji.plugin.appose.cellpose.cp3;

import static fiji.plugin.appose.ApposeUtils.transferCalibration;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;

import java.util.HashMap;
import java.util.Map;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;

import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.ImageAxisInfo;
import ij.IJ;
import ij.ImagePlus;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
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

	public static < T extends RealType< T > & NativeType< T > > void run( final ImgPlus< T > img, final Cellpose3Parameters params )
	{
		// Get axis info for the input image
		final ImageAxisInfo axisInfo = ImageAxisInfo.fromImgPlus( img );

		// Pass the param object to an Appose map.
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "use_3D", params.do3D() );
		// return null if custom model
		final String customModel = params.customModel();
		final boolean isBuiltInModel = customModel == null || customModel.equals( "" );
		inputs.put( "model", isBuiltInModel ? params.buitInModel().modelName() : null );
		inputs.put( "custom_model", isBuiltInModel ? null : customModel );
		inputs.put( "diameter", params.diameter() );
		inputs.put( "cell_channel", params.channels().get( 0 ) );
		inputs.put( "nuclei_channel", params.channels().get( 1 ) );

		inputs.put( "t_axis", axisInfo.time_axis );
		inputs.put( "stitch_threshold", params.stitchThreshold() );
		inputs.put( "z_axis", axisInfo.z_axis );
		inputs.put( "anisotropy", params.anisotropy() );
		inputs.put( "compute_flows", compute_flows );
		inputs.put( "resample", resample );
		inputs.put( "normalize", params.normalize() );
		inputs.put( "flow_threshold", params.flowThreshold() );
		inputs.put( "cellprob_threshold", params.cellProbThreshold() );
		inputs.put( "min_size", params.minSize() );
		inputs.put( "tile_overlap", tile_overlap );
		inputs.put( "flow3D_smooth", flow3d_smooth );
		inputs.put( "niter", niter == 0 ? null : niter );

		// Print out the parameters
		ApposeUtils.displayParameters( inputs );

		/*
		 * Create or retrieve the environment.
		 *
		 * The first time this code is run, Appose will create the pixi
		 * environment as specified by the cellposeEnv string, download and
		 * install the dependencies. This can take a few minutes, but it is only
		 * done once. The next time the code is run, Appose will just reuse the
		 * existing environment, so it will start much faster.
		 */
		final Environment env = Appose // the builder
				.pixi() // we chose pixi as the environment manager
				.content( cellposeEnv ) // specify the environment with the
				// string defined above
				.subscribeProgress( this::showProgress ) // report progress
				// visually
				.subscribeOutput( this::showProgress ) // report output visually
				.subscribeError( IJ::log ) // log problems
				.environment( "cp3" )
				.build(); // create the environment
		hideProgress();

		/*
		 * Using this environment, we create a service that will run the Python
		 * script.
		 */
		try (Service python = env.python().init( utilsScript ))
		{
			final Task task = python.task( cp3Script, inputs );

			// Start the script, and return to Java immediately.
			System.out.println( "Starting Cellpose-Appose task..." );
			final long start = System.currentTimeMillis();
			// To catch update message from the python script
			task.listen( e -> {
				if ( e.message != null )
				{
					this.fijiTask.setStatusMessage( e.message );
				}
				if ( e.current >= 0 )
				{
					this.fijiTask.setProgressValue( e.current );
				}
				if ( e.maximum >= 0 )
				{
					this.fijiTask.setProgressMaximum( e.maximum );
				}
			} );
			task.start();

			/*
			 * Wait for the script to finish. This will block the Java thread
			 * until the Python script is done, but it allows the Python code to
			 * run in parallel without blocking the Java thread while it is
			 * running.
			 */
			task.waitFor();
			// close the fiji task when python is done
			this.fijiTask.finish();

			// Verify that it worked.
			if ( task.status != TaskStatus.COMPLETE )
				throw new RuntimeException( "Python script failed with error: " + task.error );

			// Benchmark.
			final long end = System.currentTimeMillis();
			System.out.println( "Task finished in " + ( end - start ) / 1000. + " s" );

			/*
			 * Unwrap output.
			 *
			 * In the Python script (see below), we create a new NDArray called
			 * 'rotated' that contains the result of the processing. Here we
			 * retrieve this NDArray from the task outputs, and wrap it into a
			 * ShmImg, which is an ImgLib2 image that is backed by shared
			 * memory. We can then display this image with
			 * ImageJFunctions.show(). Note that this does not involve any
			 * copying of the data, as the NDArray and the ShmImg are both just
			 * views on the same shared memory array.
			 */
			final NDArray maskArr = ( NDArray ) task.outputs.get( "labels" );
			final Img< T > output = new ShmImg<>( maskArr );
			final ImagePlus labels = ImageJFunctions.wrap( output, "labels" );
			// Return is a TZCYX arrays, so no need of setDimensions anymore
			// labels.setDimensions( 1, labels.getNChannels(),
			// labels.getNFrames() );
			// labels.getProcessor().resetMinAndMax();
			final StackStatistics stats = new StackStatistics( labels );
			labels.setDisplayRange( stats.min, stats.max );
			useGlasbeyDarkLUT( labels );
			transferCalibration( imp, labels );
			labels.show();

			if ( return_ROIs )
			{
				ApposeUtils.addROIs( labels );
			}

			if ( compute_flows )
			{
				// RGB image returned
				final NDArray flowsArr = ( NDArray ) task.outputs.get( "flows" );
				final Img< T > flows = new ShmImg<>( flowsArr );
				final ImagePlus flowsImp = ImageJFunctions.wrap( flows, "flows" );
				// Return is a TZCYX arrays, so no need of setDimensions anymore
				// flowsImp.setDimensions( 3, flowsImp.getNChannels(),
				// flowsImp.getNFrames() );
				flowsImp.getProcessor().resetMinAndMax();
				transferCalibration( imp, flowsImp );
				flowsImp.show();
			}

		}
		catch ( final Exception e )
		{
			IJ.handleException( e );
		}

	}

}
