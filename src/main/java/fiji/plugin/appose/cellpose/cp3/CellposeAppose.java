
package fiji.plugin.appose.cellpose.cp3;

import static fiji.plugin.appose.ApposeUtils.getBestTorchConfig;
import static fiji.plugin.appose.ApposeUtils.rawWraps;
import static fiji.plugin.appose.ApposeUtils.transferCalibration;
import static fiji.plugin.appose.ApposeUtils.useGlasbeyDarkLUT;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.task.TaskService;

import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.ImageAxisInfo;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.process.StackStatistics;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/*
 * This class implements the cellpose v3 Fiji plugin that calls native Python code with Appose.
 *
 * The python script (cp3.py) receives an image as input, and parameters specified by the user in the GUI, 
 * and run a cellpose segmentation with the native Cellpose code. The resulting mask is then sent back to Fiji and displayed as a new image.
 */

@Plugin( type = Command.class, menuPath = "Plugins>Segmentation>Cellpose-Appose>Cellpose..." )
public class CellposeAppose extends DynamicCommand implements Initializable
{
	@Parameter
	private TaskService taskService;
	
	@Parameter
    private PrefService prefService; 

	@Parameter(label="", visibility=ItemVisibility.MESSAGE)
    private final String messageTitle = "<html>" +
            "<table><tr valign='top'><td>" +
            "<h2>Cell Detection using Cellpose (v3) brought to you by Appose !</h2>" +
            "<a href='https://github.com/mouseland/cellpose'>https://github.com/mouseland/cellpose</a>" +
			" <font face='Courier New' size='5'>&#9829;</font> " +
			"<a href='https://apposed.org/'>https://apposed.org/</a>" +
            "<br/><br/><small>Please cite the Cellpose paper if this tool was useful to you: <a href='https://doi.org/10.1101/2024.02.10.579780'>https://doi.org/10.1101/2024.02.10.579780</a></small>" +
            "</td><td>&nbsp;&nbsp;<img src='"+this.getClass().getResource("/cp_logo.png")+"' width='100' height='100'></img><td>" +
            "</tr></table>" +
            "</html>";

    // ---------
			
	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><b>Cellpose Parameters</b></html>")
    private final String initMsg = "<html><hr width='100'></html>";

	@Parameter( label = "Cellpose model", choices = { "cyto3", "nuclei", "tissunet", "livecell", "CP", "cyto2", "cyto2_cp3", "tissuenet_cp3",
			"livecell_cp3", "yeast_PhC_cp3", "yeast_BF_cp3", "bact_phase_cp3", "bact_fluor_cp3", "deepbacs_cp3",
			"neurips_grayscale_cyto2", "TN1", "TN2", "TN3", "LC1", "LC2", "LC3", "LC4", "neurips_cellpose_default",
			"neurips_cellpose_transformer" }, description = "Choose CP model to run" )
	private String cp_model = "cyto3"; // cellpose model to use, ignored if custom model path is provided

	@Parameter( label = "Path to custom model", description = "Custom model path, overrides the Cellpose model", required = false )
	private String custom_model = ""; // path to custom model, if empty use the selected Cellpose model

	@Parameter( label = "Diameter", min = "0", description = "Average diameter of a cell/nuclei (in pixels)" )
	private int cell_diameter = 30; // cell diameter

	@Parameter( label = "Cytoplasmic channel", choices = { "None", "Average" }, description = "Channel index of the cytoplasmic channel. N/A for none" )
	private String cyto_channel = "None"; // cytoplasmic channel to segment

	@Parameter( label = "Nuclei channel", choices = { "None" }, description = "Channel index of the nuclei channel. N/A for none" )
	private String nuclei_channel = "None"; // nuclei channel to segment

	@Parameter( label = "Minimum Object Size", min = "0", description = "Minimum object size (in pixels) to keep" )
	private int min_size = 15; // minimum object size (in pixels)

	@Parameter( label = "Normalize Channel Intensity", description = "Normalize intensity on each channels" )
	private Boolean normalize = true; // intensity normalization

	@Parameter( label = "Resample Segmentation", description = "Resample detection to image scale for smoother output" )
	private Boolean resample = true; // resample mask (slower but nicer)

	@Parameter( label = "return ROIs", description = "Return the ROIs (only in 2D)" )
	private Boolean return_ROIs = false; // if true return ROIs (Note: only for 2D image)

    // ---------

	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><b>Advanced Options</b></html>")
    private final String advMsg = "<html><hr width='100'></html>";

	@Parameter( label = "Cell probability threshold", min = "-6.0", max = "6.0", description = "Threshold on cell detection", stepSize = "0.1" )
	private double cellprob_threshold = 0.0;

	@Parameter( label = "Flows Threshold", min = "0", max = "1", description = "Threshold on flows to detect objects (only for 2D)", stepSize = "0.1" )
	private double flow_threshold = 0.4; // probability threshold on flows

	@Parameter( label = "Tile overlap", min = "0", max = "1", description = "Overlap ratio between tiles", stepSize = "0.1" )
	private double tile_overlap = 0.1; // overlap ration between cellpose tiles

	@Parameter( label = "Compute Flows", description = "Compute the segmentation flows output" )
	private Boolean compute_flows = false; // whether to compute flows channel

	@Parameter( label = "Mode 3D", choices = { "None" }, description = "Mode of 3D segmentation", visibility=ItemVisibility.MESSAGE )
	private String mode_3d = "None"; // mode 3D of CP to use, only for 3D image

	private boolean is3D = false;

	@Parameter( label="Stitch threshold", min="0.0", max="1.0", description="\"2D+stitch mode only: IOU threshold to stitch labels together along the Z-axis\"", visibility=ItemVisibility.MESSAGE )
	private Double stitch_threshold = 0.1; 
	
	@Parameter( label="Flow3d smooth", min="0", description="3D mode only: Gaussian smoothing sigma applied on flows.", visibility=ItemVisibility.MESSAGE ) 
	private Integer flow3d_smooth = 0; // gaussian smooth of 3D flows
	
	@Parameter( label="Iterations", min="0", description="Number of iterations for flow computations (niter parameter). Increase it (eg 1000,2000) for elongated shapes" ) 
	private Integer niter = 0; // number of iterations. If 0, put None and use default

	// ---------
	
	private boolean use3d = false;

	private double anisotropy = 1.0;

	private ImageAxisInfo axis_info; // position of the different axes

	// Fiji task
	private org.scijava.task.Task fijiTask;

	/*
	 * Initialize the plugin.
	 * This method is called when the plugin is loaded, and it is used to initialize the plugin parameters.
	 * Check for Image correctness, manage parameters visibility and choices based on the image properties (2D vs 3D, number of channels, etc).
	 */
	@Override
	public void initialize()
	{
		// Grab the current image (last touched image in Fiji)
		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( imp == null )
		{
			// ToDo: Find a cleaner way to exit, the "return" still trigger the
			// plugin interface, I needed to throw an exception for the process to stop.
			IJ.error( "No image available to process" );
			throw new RuntimeException( "No image available to process" );
		}

		is3D = ApposeUtils.is3d( imp );

		final List< String > channelChoices = ApposeUtils.getChannelChoices( imp, true );

		// Set the max possible value of channels based on image dimension
		final MutableModuleItem< String > cytoItem =
				getInfo().getMutableInput( "cyto_channel", String.class );
		cytoItem.setChoices( channelChoices );

		final MutableModuleItem< String > nucItem =
				getInfo().getMutableInput( "nuclei_channel", String.class );
		nucItem.setChoices( channelChoices );

		// Extend GUI with extra 3D options if the image is 3D
		if ( is3D )
		{
			final List< String > modeChoices = Arrays.asList( "2D+stitch", "3D" );
			final MutableModuleItem< String > mode3dItem =
					getInfo().getMutableInput( "mode_3d", String.class );
			mode3dItem.setChoices( modeChoices );
			mode3dItem.setVisibility(ItemVisibility.NORMAL);

			final MutableModuleItem< Integer > flowItem = 
					getInfo().getMutableInput( "flow3d_smooth", Integer.class );
			flowItem.setMinimumValue( 0 );
			flowItem.setVisibility(ItemVisibility.NORMAL);
			
			final MutableModuleItem< Double > stitchItem = 
					getInfo().getMutableInput( "stitch_threshold", Double.class );
			stitchItem.setMinimumValue( 0.0 );
			stitchItem.setMaximumValue( 1.0 );
			stitchItem.setStepSize( 0.05 );
			stitchItem.setVisibility(ItemVisibility.NORMAL);					
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
		// start task
		fijiTask = taskService.createTask( "cellpose-appose" );
		fijiTask.setStatusMessage( "Launching Cellpose appose task." );
		fijiTask.start();

		// Grab the current image.
		final ImagePlus imp = WindowManager.getCurrentImage();
		try
		{
			// Get extra parameters for 3D if needed
			final boolean is3D = ApposeUtils.is3d( imp );

			use3d = false;
			if ( is3D )
			{
				final String mode = mode_3d;
				final Calibration cal = imp.getCalibration();
				anisotropy = cal.pixelDepth / cal.pixelHeight;
				if ( mode.equals( "3D" ) )
				{
					use3d = true;
				}
				
				if ( ( stitch_threshold <= 0.0 ) & ( mode.equals( "2D+stitch" ) ) )
				{
					IJ.error( "stitch_threshold should be between 0 and 1 if 2D+stitch, " + stitch_threshold + " was provided" );
					return;
				}

				if ( return_ROIs )
				{
					IJ.error( "ROIs are not compatible for 3D images, switching to 3D label output." );
					return_ROIs  = false;
				}
			}
			else
			{
				stitch_threshold = 0.0; // ensure it's 0
			}
			// get the z_axis number in what python should receive
			axis_info = ApposeUtils.getImageAxisInfo( imp );

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
	 * Start the Appose processing on the Image
	 */
	public < T extends RealType< T > & NativeType< T > > void process( final ImagePlus imp ) throws IOException, BuildException
	{
		// Print os and arch info
		System.out.println( "Starting process..." );

		// Fetch the pixi environment specification
		final String cellposeEnv = pixiEnv();
		
		// Load python scripts from resources
		final String utilsScript = IOUtils.toString(
				getClass().getResource( "/cp_utils.py" ), StandardCharsets.UTF_8 );
		final String cp3Script = IOUtils.toString(
				getClass().getResource( "/cp3.py" ), StandardCharsets.UTF_8 );

		// Wrap the ImagePlus into a ImgLib2 image.
		final ImgPlus< T > img = rawWraps( imp );

		// Add inputs and parameters to a map, that will be sent to the Python script. 
		// - The keys of the map should match the argument names of the Python script (see cp3.py for this example).
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "use_3D", use3d );
		inputs.put( "model_name", ( custom_model.equals("") ) ? cp_model : null );
		inputs.put( "custom_model", ( custom_model.equals("") ) ? null : custom_model );
		inputs.put( "diameter", cell_diameter );
		inputs.put( "cell_channel", ApposeUtils.convertChannelChoiceToInt( cyto_channel, true ) );
		inputs.put( "nuclei_channel", ApposeUtils.convertChannelChoiceToInt( nuclei_channel, true ) );
		inputs.put( "t_axis", axis_info.time_axis);
		inputs.put( "stitch_threshold", stitch_threshold );
		inputs.put( "z_axis", axis_info.z_axis );
		inputs.put( "anisotropy", anisotropy );
		inputs.put( "compute_flows", compute_flows );
		inputs.put( "resample", resample );
		inputs.put( "normalize", normalize );
		inputs.put( "flow_threshold", flow_threshold );
		inputs.put( "cellprob_threshold", cellprob_threshold );
		inputs.put( "min_size", min_size );
		inputs.put( "tile_overlap", tile_overlap );
		inputs.put( "flow3D_smooth", flow3d_smooth );
		inputs.put( "niter", niter==0?null:niter );
		
		// Print out the parameters for debugging
		ApposeUtils.displayParameters( inputs );

		String envSuffix = getBestTorchConfig();
		//System.err.println("Selected environment suffix used: " + envSuffix);

		// Install the environment if needed
		final Environment env = Appose // the builder
				.pixi() // we chose pixi as the environment manager
				.content( cellposeEnv ) // specify the environment with the
				// string defined above
				.subscribeProgress( this::showProgress ) // report progress
				// visually
				.subscribeOutput( this::showProgress ) // report output visually
				.subscribeError( IJ::log ) // log problems
				.environment( "cp3" + envSuffix )
				.build(); // create the environment
		hideProgress();

		// Using this environment, we create a service that will run the Python
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
					//System.out.println(e.message);
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

			// Block Java thread until the python script is done
			task.waitFor();

			// close the fiji task when python is done
			this.fijiTask.finish();

			// Verify that it worked.
			if ( task.status != TaskStatus.COMPLETE )
				throw new RuntimeException( "Python script failed with error: " + task.error );

			// Benchmark.
			final long end = System.currentTimeMillis();
			System.out.println( "Task finished in " + ( end - start ) / 1000. + " s" );

			// Unwrap the output back into an ImagePlus and display it. 
			final NDArray maskArr = ( NDArray ) task.outputs.get( "labels" );
			final Img< T > output = new ShmImg<>( maskArr );
			final ImagePlus labels = ImageJFunctions.wrap( output, "labels" );
			
			final StackStatistics stats = new StackStatistics(labels);
			labels.setDisplayRange(stats.min, stats.max);
			useGlasbeyDarkLUT( labels );
			transferCalibration( imp, labels );
			labels.show();

			// Optionally add ROIs to the ROI manager if the user selected this option (only for 2D images)
			if ( return_ROIs )
			{
				ApposeUtils.addROIs( labels );
			}

			// Optionally display flows if the user selected this option (only for 2D images)
			if ( compute_flows )
			{
				// RGB image returned
				final NDArray flowsArr = ( NDArray ) task.outputs.get( "flows" );
				final Img< T > flows = new ShmImg<>( flowsArr );
				final ImagePlus flowsImp = ImageJFunctions.wrap( flows, "flows" );
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
	 * Fetch the pixi environment specification.
	 *
	 * This is a YAML specification of a pixi environment, that specifies the
	 * dependencies that we need in Python to run our script.
	 */
	public String pixiEnv()
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
}
