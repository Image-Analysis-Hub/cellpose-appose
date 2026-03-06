package fiji.plugin.appose.cellpose.cp3;

import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static fiji.plugin.appose.ApposeUtils.transferCalibration;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.RuntimeErrorException;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.scijava.Initializable;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import fiji.plugin.appose.ApposeUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/*
 * This class implements an example of a classical Fiji plugin (not ImageJ2 plugin), 
 * that calls native Python code with Appose.
 * 
 * We use a simple examples of rotating an input image by 90 degrees, using the scikit-image 
 * library in Python, and returning the result back to Fiji. Everything is contained in a 
 * single class, but you can imagine restructuring the code and the Python script as you see fit.
 */

@Plugin( type = Command.class, menuPath = "Plugins>Cellpose-Appose>Cellpose appose" )
public class CellposeAppose extends DynamicCommand implements Initializable
{

	@Parameter( label = "Cellpose model", choices = { "cyto3", "nuclei", "tissunet", "livecell", "CP", "cyto2", "cyto2_cp3", "tissuenet_cp3",
			"livecell_cp3", "yeast_PhC_cp3", "yeast_BF_cp3", "bact_phase_cp3", "bact_fluor_cp3", "deepbacs_cp3",
			"neurips_grayscale_cyto2", "TN1", "TN2", "TN3", "LC1", "LC2", "LC3", "LC4", "neurips_cellpose_default",
			"neurips_cellpose_transformer" }, description = "Choose CP model to run" )
	private String cp_model = "cyto3"; // cellpose model

	@Parameter( label = "Custom model", description = "Custom model path, overrides the Cellpose model", style = "file", required = false, validater = "validateCustomModel" )
	private File custom_model = null;

	@Parameter( label = "Diameter", min = "0", description = "Average diameter of a cell/nuclei (in pixels)" )
	private int cell_diameter = 30; // cell diameter

	@Parameter( label = "Cytoplasmic channel", choices = { "N/A" }, description = "Channel index of the cytoplasmic channel. N/A for none" )
	private String cyto_channel = "None"; // cytoplasmic channel to segment

	@Parameter( label = "Nuclei channel", choices = { "N/A" }, description = "Channel index of the nuclei channel. N/A for none" )
	private String nuclei_channel = "None"; // nuclei channel to segment

	@Parameter( label = "Compute Flows", description = "Compute the segmentation flows output" )
	private Boolean compute_flows = false; // whether to compute flows channel

	@Parameter( label = "Flows Threshold", min = "0", max = "1", description = "Threshold on flows to detect objects (only for 2D)", stepSize = "0.1" )
	private double flow_threshold = 0.4; // probability threshold on flows

	@Parameter( label = "Minimum Object Size", min = "0", description = "Minimum object size (in pixels) to keep" )
	private int min_size = 15; // minimum object size

	@Parameter( label = "Tile overlap", min = "0", max = "1", description = "Overlap ratio between tiles", stepSize = "0.1" )
	private double tile_overlap = 0.1; // overlap ration between cellpose tiles

	@Parameter( label = "Normalize", description = "Normalize intensity on each channels" )
	private Boolean normalize = true; // intensity normalization before
										// prediction

	@Parameter( label = "Resample", description = "Resample detection to image scale for smoother output" )
	private Boolean resample = true; // resample mask (slower but nicer)

	private boolean is3D = false;

	private MutableModuleItem< String > mode_3d; // mode 3D of CP to use, only
													// for 3D image

	private MutableModuleItem< Double > stitch_threshold; // stitching value,
															// only for 3D image

	private MutableModuleItem< Integer > flow3D_smooth; // gaussian smooth of
														// the 3D flows (only
														// with use3d = true)

	private int flow3D_smooth_value = 0;

	private double stitch_threshold_value = 0;

	private boolean use3d = false;

	private double anisotropy = 1.0;

	private Object z_axis = null; // z_axis position

	// Advance parameters
	// ToDo: make them available in the GUI
	private double cellprob_threshold = 0.0;

	@Override
	public void initialize()
	{
		// Grab the current image.
		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( imp == null )
		{
			// ToDo: Find a cleaner way to exit, the "return" still trigger the
			// plugin interface
			// I needed to throw an exception for the process to stop.
			IJ.error( "No image available to process" );
			throw new RuntimeException( "No image available to process" );
		}

		is3D = ApposeUtils.is3d( imp );

		List< String > channelChoices = ApposeUtils.getChannelChoices( imp );

		// Set the max possible value of channels based on image dimension
		final MutableModuleItem< String > cytoItem =
				getInfo().getMutableInput( "cyto_channel", String.class );
		cytoItem.setChoices( channelChoices );

		final MutableModuleItem< String > nucItem =
				getInfo().getMutableInput( "nuclei_channel", String.class );
		nucItem.setChoices( channelChoices );

		// Set the 3D mode selected by the user if the image is 3D
		if ( is3D )
		{
			mode_3d = new DefaultMutableModuleItem<>( getInfo(),
					"Mode 3d", String.class );
			mode_3d.setChoices( Arrays.asList( "2D+stitch", "3D" ) );
			mode_3d.setDescription( "Run Cellpose in 3D (xy, yx, xz) or in 2D and stitch the labels." );
			getInfo().addInput( mode_3d );

			flow3D_smooth = new DefaultMutableModuleItem<>( getInfo(),
					"flow3D smooth", Integer.class );
			flow3D_smooth.setMinimumValue( 0 );
			flow3D_smooth.setDescription( "3D mode only: Gaussian smoothing sigma applied on flows." );
			getInfo().addInput( flow3D_smooth );

			stitch_threshold = new DefaultMutableModuleItem<>( getInfo(),
					"Stitch threshold", Double.class );
			stitch_threshold.setMaximumValue( 1.0 );
			stitch_threshold.setMinimumValue( 0.0 );
			stitch_threshold.setStepSize( 0.1 );
			stitch_threshold.setDescription( "2D+stitch mode only: IOU threshold to stitch labels together along the Z-axis" );
			getInfo().addInput( stitch_threshold );
		}
	}

	/*
	 * This is the entry point for the plugin. This is what is called when the
	 * user select the plugin menu entry: 'Plugins > Examples >
	 * ApposeFijiPluginExample' in our case. You can redefine this by editing
	 * the file 'plugins.config' in the resources directory
	 * (src/main/resources).
	 */
	@Override
	public void run()
	{
		// Grab the current image.
		final ImagePlus imp = WindowManager.getCurrentImage();
		try
		{
			// Get the parameters based on the image properties
			final boolean is3D = ApposeUtils.is3d( imp );
			final int nchanels = imp.getNChannels();
			// getParameters( is3D, nchanels );

			use3d = false;
			if ( is3D )
			{
				final String mode = mode_3d.getValue( this );
				final Calibration cal = imp.getCalibration();
				anisotropy = cal.pixelDepth / cal.pixelHeight;
				if ( mode.equals( "3D" ) )
				{
					use3d = true;
					flow3D_smooth_value = flow3D_smooth.getValue( this );
				}
				else
				{
					stitch_threshold_value = stitch_threshold.getValue( this );
				}
			}
			// get the z_axis number in what python should receive
			z_axis = ApposeUtils.getZAxis( imp );

			// Runs the processing code.
			process( imp );
		}
		catch ( final IOException | BuildException e )
		{
			IJ.error( "An error occurred: " + e.getMessage() );
			e.printStackTrace();
		}

	}

	/*
	 * Actually do something with the image.
	 */
	public < T extends RealType< T > & NativeType< T > > void process( final ImagePlus imp ) throws IOException, BuildException
	{
		// Print os and arch info
		System.out.println( "Starting process..." );

		/*
		 * For this example we use pixi to create a Python environment with the
		 * necessary dependencies. It is specified with a string that contains a
		 * YAML specification of the environment, similar to what you would put
		 * in an environment.yaml file. You could load it from an existing file,
		 * be here for simplicity it is directly returned as a string. See the
		 * corresponding method.
		 */
		final String cellposeEnv = pixiEnv();
		/*
		 * The Python script that we want to run. It is specified as a string,
		 * but it could be loaded from an existing .py file. In our case the
		 * script is very simple and has no parameters. We give details on how
		 * to pass input and receive outputs below.
		 */
		final String utilsScript = IOUtils.toString(
				getClass().getResource( "/cp_utils.py" ), StandardCharsets.UTF_8 );
		final String cp3Script = IOUtils.toString(
				getClass().getResource( "/cp3.py" ), StandardCharsets.UTF_8 );

		/*
		 * The following wraps an ImageJ ImagePlus into an ImgLib2 Img, and then
		 * into an Appose NDArray, which is a shared memory array that can be
		 * passed to Python without copying the data.
		 * 
		 * As an ImagePlus is not mapped on a shared memory array, the ImgLib2
		 * image wrapping the ImagePlus is actually copied to a shared memory
		 * image (the ShmImg) when we wrap it into an NDArray. This is because
		 * the NDArray needs to be backed by a shared memory array in order to
		 * be passed to Python without copying the data. We could have avoided
		 * this copy by directly loading the image into a ShmImg in the first
		 * place, but for simplicity we start with an ImagePlus and show how to
		 * wrap it into a shared memory array.
		 */

		// Wrap the ImagePlus into a ImgLib2 image.
		final ImgPlus< T > img = rawWraps( imp );

		/*
		 * Copy the image into a shared memory image and wrap it into an
		 * NDArray, then store it in an input map that we will pass to the
		 * Python script.
		 * 
		 * Note that we could have passed multiple inputs to the Python script
		 * by putting more entries in the input map, and they would all be
		 * available in the Python script as shared memory NDArrays.
		 * 
		 * A ND array is a multi-dimensional array that is stored in shared
		 * memory, that can be unwrapped as a NumPy array in Python, and wrapped
		 * as a ImgLib2 image in Java.
		 * 
		 */
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "use_3D", use3d );
		// return null if custom model
		inputs.put( "model", ( custom_model == null ) ? cp_model : null );
		inputs.put( "custom_model", ( custom_model == null ) ? null : custom_model.toString() );
		inputs.put( "diameter", cell_diameter );
		inputs.put( "cell_channel", parseChannelChoice( cyto_channel ) );
		inputs.put( "nuclei_channel", parseChannelChoice( nuclei_channel ) );
		inputs.put( "stitch_threshold", stitch_threshold_value );
		inputs.put( "z_axis", z_axis );
		inputs.put( "anisotropy", anisotropy );
		inputs.put( "compute_flows", compute_flows );
		inputs.put( "resample", resample );
		inputs.put( "normalize", normalize );
		inputs.put( "flow_threshold", flow_threshold );
		inputs.put( "cellprob_threshold", cellprob_threshold );
		inputs.put( "min_size", min_size );
		inputs.put( "tile_overlap", tile_overlap );
		inputs.put( "flow3D_smooth", flow3D_smooth_value );
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
				System.out.println( "\tInfo: " + e.message );
			} );
			task.start();

			/*
			 * Wait for the script to finish. This will block the Java thread
			 * until the Python script is done, but it allows the Python code to
			 * run in parallel without blocking the Java thread while it is
			 * running.
			 */
			task.waitFor();

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
			labels.getProcessor().resetMinAndMax();
			useGlasbeyDarkLUT( labels );
			transferCalibration( imp, labels );
			labels.show();

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

	/*
	 * The environment specification.
	 * 
	 * This is a YAML specification of a pixi environment, that specifies the
	 * dependencies that we need in Python to run our script. In this case we
	 * need scikit-image for the rotation, and appose to be able to receive the
	 * input and send the output back to Fiji. Note that we specify appose as a
	 * pip dependency, as it is not available on conda-forge.
	 * 
	 * Most likely in your scripts the dependencies will be different, but you
	 * will always need appose.
	 */
	private String pixiEnv()
	{
		String env = "";
		try
		{
			final URL pixiFile = this.getClass().getResource( "/pixi.toml" );
			env = IOUtils.toString( pixiFile, StandardCharsets.UTF_8 );

		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return env;
	}

	// Helper functions to display progress while building the Appose
	// environment.
	// Temporary solution until Appose has a nicer built-in way to do this.

	private volatile JDialog progressDialog;

	private volatile JProgressBar progressBar;

	private void showProgress( final String msg )
	{
		showProgress( msg, null, null );
	}

	private void showProgress( final String msg, final Long cur, final Long max )
	{
		EventQueue.invokeLater( () -> {
			if ( progressDialog == null )
			{
				final Window owner = IJ.getInstance();
				progressDialog = new JDialog( owner, "Fiji ♥ Appose" );
				progressDialog.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
				progressBar = new JProgressBar();
				progressDialog.getContentPane().add( progressBar );
				progressBar.setFont( new Font( "Courier", Font.PLAIN, 14 ) );
				progressBar.setString(
						"--------------------==================== " +
								"Building Python environment " +
								"====================--------------------" );
				progressBar.setStringPainted( true );
				progressBar.setIndeterminate( true );
				progressDialog.pack();
				progressDialog.setLocationRelativeTo( owner );
				progressDialog.setVisible( true );
			}
			if ( msg != null && !msg.trim().isEmpty() )
				progressBar.setString( "Building Python environment: " + msg.trim() );
			if ( cur != null || max != null )
				progressBar.setIndeterminate( false );
			if ( max != null )
				progressBar.setMaximum( max.intValue() );
			if ( cur != null )
				progressBar.setValue( cur.intValue() );
		} );
	}

	private void hideProgress()
	{
		EventQueue.invokeLater( () -> {
			if ( progressDialog != null )
				progressDialog.dispose();
			progressDialog = null;
		} );
	}

	public static Integer parseChannelChoice( String str )
	{
		if ( str == null || str.equalsIgnoreCase( "None" ) )
		{ return null; }
		return Integer.parseInt( str );
	}

	public void validateCustomModel()
	{
		if ( custom_model != null )
		{
			if ( !custom_model.exists() )
			{
				IJ.error( "The path " + custom_model.toString() + " does not exist !" );
				throw new RuntimeException( "The path " + custom_model.toString() + " does not exist !" );
			}
		}
	}

}
