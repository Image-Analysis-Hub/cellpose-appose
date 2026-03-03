package fiji.plugin.appose.cellpose;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.scijava.Initializable;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;
import org.scijava.module.DefaultMutableModuleItem;

import ij.IJ;
import net.imagej.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

/*
 * This class implements an example of a classical Fiji plugin (not ImageJ2 plugin), 
 * that calls native Python code with Appose.
 * 
 * We use a simple examples of rotating an input image by 90 degrees, using the scikit-image 
 * library in Python, and returning the result back to Fiji. Everything is contained in a 
 * single class, but you can imagine restructuring the code and the Python script as you see fit.
 */

@Plugin(type = Command.class, menuPath = "Plugins>Cellpose-Appose>Cellpose appose")
public class CellposeAppose extends DynamicCommand implements Initializable
{
	
	@Parameter( choices = {"cyto3", "nuclei", "tissunet", "livecell", "CP", "cyto2", "cyto2_cp3", "tissuenet_cp3",
			"livecell_cp3", "yeast_PhC_cp3", "yeast_BF_cp3", "bact_phase_cp3", "bact_fluor_cp3", "deepbacs_cp3", 
			"neurips_grayscale_cyto2", "TN1", "TN2", "TN3", "LC1", "LC2", "LC3", "LC4", "neurips_cellpose_default", 
			"neurips_cellpose_transformer"} )
	private String cp_model = "cyto3"; // cellpose model
	
	@Parameter( label = "Diameter", min="0" )
	private int cell_diameter = 30; // cell diameter
	
	@Parameter(label="Cytoplasmic chanel", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private int cyto_chanel = 1;  // cytoplasmic channel to segment
	
	@Parameter(label="Nuclei chanel", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private int nuclei_chanel = 1;  // cytoplasmic channel to segment
	
	
	private boolean is3D = false;

	private MutableModuleItem<String> mode_3d; // mode 3D of CP to use, only for 3D image
	private MutableModuleItem<Double> stitch_threshold; // stitching value, only for 3D image
	
	private double stitch_threshold_value = 0;
	private boolean use3d = false;
	private double anisotropy = 1.0;
	
	@Override
	public void initialize() {
		// Grab the current image.
		final ImagePlus imp = WindowManager.getCurrentImage();
		is3D = is3d( imp );
		
		// Set the max possible value of channels based on image dimension
		final MutableModuleItem<Integer> cytoItem = 
			getInfo().getMutableInput("cyto_chanel", Integer.class);
		cytoItem.setMaximumValue(imp.getNChannels() - 1);
		
		final MutableModuleItem<Integer> nucItem = 
				getInfo().getMutableInput("nuclei_chanel", Integer.class);
			nucItem.setMaximumValue(imp.getNChannels() - 1);
			
		// Set the 3D mode selected by the user if the image is 3D
		if (is3D)
		{
			 mode_3d = new DefaultMutableModuleItem<>(getInfo(),
					"mode_3d", String.class);
			 mode_3d.setChoices( Arrays.asList("2D+stitch", "3D"));
			getInfo().addInput(mode_3d);
			
			 stitch_threshold = new DefaultMutableModuleItem<>(getInfo(),
						"stitch_threshold", Double.class);
			 stitch_threshold.setMaximumValue( 1.0 );
			 stitch_threshold.setMinimumValue( 0.0 );
				getInfo().addInput(stitch_threshold);
				
		}
	}

	/*
	 * Check if the Image is 3D or 2D
	 */
	public boolean is3d(ImagePlus imp)
	{
		return imp.getNSlices() > 1;
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
			boolean is3D = is3d( imp );
			int nchanels = imp.getNChannels();
			//getParameters( is3D, nchanels );
			
			use3d = false;
			if ( is3D ) {
				String mode = mode_3d.getValue(this);
				Calibration cal = imp.getCalibration();
				anisotropy = cal.pixelDepth / cal.pixelHeight;
				if (mode.equals("3D")) {
					use3d = true;
				} else {
					stitch_threshold_value = stitch_threshold.getValue(this);
				}
			}
			
			// Runs the processing code.
			process( imp );
		}
		catch ( final IOException | BuildException e ) {
			IJ.error("An error occurred: " + e.getMessage());
			e.printStackTrace();
		}

	}

	/**
	 * Displays the parameters used in a formatted manner
	 * @param inputs the map containing all input parameters
	 */
	private void displayParameters(Map<String, Object> inputs) {
		System.out.println("Parameters used: ");
		System.out.println("─".repeat(50));

		inputs.forEach((key, value) -> {
			if (!key.equals("image") && !key.equals("cell_channel") && !key.equals("nuclei_channel")) {
				System.out.printf("  %-20s: %s%n", key, value);
			}
		});

		// Add combined channel line
		int cellChannel = (int) inputs.get("cell_channel");
		int nucleiChannel = (int) inputs.get("nuclei_channel");
		System.out.printf("  %-20s: [%d, %d]%n", "channel[cell,nuclei]", cellChannel, nucleiChannel);

		System.out.println("─".repeat(50));
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
		final String script = getScript();

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
		@SuppressWarnings( "unchecked" )
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
		inputs.put( "model", cp_model );
		inputs.put( "diameter", cell_diameter );
		inputs.put( "cell_channel", cyto_chanel );
		inputs.put( "nuclei_channel", nuclei_chanel );
		inputs.put( "stitch_threshold", stitch_threshold_value );
		inputs.put( "z_axis", 0 ); // @TODO: where is the z_axis in the shared object ?
		inputs.put( "anisotropy", anisotropy );
		// Print out the parameters
		displayParameters( inputs );

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
				.content( cellposeEnv ) // specify the environment with the string defined above
				.subscribeProgress( this::showProgress ) // report progress visually
				.subscribeOutput( this::showProgress ) // report output visually
				.subscribeError( IJ::log ) // log problems
				.build(); // create the environment
		hideProgress();

		/*
		 * Using this environment, we create a service that will run the Python
		 * script.
		 */
		try ( Service python = env.python() )
		{
			/*
			 * With this service, we can now create a task that will run the
			 * Python script with the specified inputs. This command takes the
			 * script as first argument, and a map of inputs as second argument.
			 * The keys of the map will be the variable names in the Python
			 * script, and the values are the data that will be passed to
			 * Python.
			 */
			final Task task = python.task( script, inputs );

			// Start the script, and return to Java immediately.
			System.out.println( "Starting Cellpose-Appose task..." );
			final long start = System.currentTimeMillis();
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
			ImageJFunctions.show( output );
			// Et voilà!
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
		try {
			URL pixiFile = this.getClass().getResource("/pixi.toml");
			env = IOUtils.toString(pixiFile, StandardCharsets.UTF_8);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return env;
	}

	/*
	 * The Python script.
	 * 
	 * This is the Python code that will be run by the service. It is specified
	 * as a string here for simplicity, but it could be loaded from an existing
	 * .py file. In this example, the script receives an input image as a shared
	 * memory NDArray (the 'image' variable), rotates it by 90 degrees using
	 * scikit-image, and then sends the result back to Fiji by creating a new
	 * NDArray (the 'rotated' variable) and putting it in the task outputs.
	 * 
	 * The string is monolithic and has not parameters for simplicity, but you
	 * can imagine more complex scripts that take multiple inputs, have
	 * parameters, call functions defined in other .py files, etc. The only
	 * requirement is that the script can be run as a standalone script, and
	 * that it uses the appose library to receive inputs and send outputs.
	 * 
	 * To pass on-the-fly parameters, you can:
	 * 
	 * 1/ modify the string below before creating the task, by using replace,
	 * string concatenation, string format, or any other method to inject the
	 * parameters into the script string before it is run. This approach
	 * requires you to write the script as a template with placeholders for the
	 * parameters, and then fill in the placeholders with the actual parameters
	 * when you create the task.
	 * 
	 * 2/or you can use the input map to pass parameters as well, by putting
	 * them in the map with a specific key.
	 */
	private String getScript()
	{
		String script = "";
		try {
			URL scriptFile = this.getClass().getResource("/cp3.py");
			script = IOUtils.toString(scriptFile, StandardCharsets.UTF_8);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return script;
	}
	
	// Helper functions to display progress while building the Appose environment.
	// Temporary solution until Appose has a nicer built-in way to do this.

	private JDialog progressDialog;
	private JProgressBar progressBar;
	private void showProgress( String msg )
	{
		showProgress( msg, null, null );
	}
	private void showProgress( String msg, Long cur, Long max )
	{
		EventQueue.invokeLater( () ->
		{
			if ( progressDialog == null ) {
				Window owner = IJ.getInstance();
				progressDialog = new JDialog( owner, "Fiji ♥ Appose" );
				progressDialog.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
				progressBar = new JProgressBar();
				progressDialog.getContentPane().add( progressBar );
				progressBar.setFont( new Font( "Courier", Font.PLAIN, 14 ) );
				progressBar.setString(
					"--------------------==================== " +
					"Building Python environment " +
					"====================--------------------"
				);
				progressBar.setStringPainted( true );
				progressBar.setIndeterminate( true );
				progressDialog.pack();
				progressDialog.setLocationRelativeTo( owner );
				progressDialog.setVisible( true );
			}
			if ( msg != null && !msg.trim().isEmpty() ) progressBar.setString( "Building Python environment: " + msg.trim() );
			if ( cur != null || max != null ) progressBar.setIndeterminate( false );
			if ( max != null ) progressBar.setMaximum( max.intValue() );
			if ( cur != null ) progressBar.setValue( cur.intValue() );
		} );
	}
	private void hideProgress()
	{
		EventQueue.invokeLater( () ->
		{
			progressDialog.dispose();
			progressDialog = null;
		} );
	}

	/*
	 * A utility to pretty print things. Probably will go away in your code.
	 */
	private static String indent( final String script )
	{
		final String[] split = script.split( "\n" );
		String out = "";
		for ( final String string : split )
			out += "    " + string + "\n";
		return out;
	}

	/*
	 * A utility to wrap an ImagePlus into an ImgPlus, without too many
	 * warnings. Hacky.
	 */
	@SuppressWarnings( "rawtypes" )
	public static final ImgPlus rawWraps( final ImagePlus imp )
	{
		final ImgPlus< DoubleType > img = ImagePlusAdapter.wrapImgPlus( imp );
		final ImgPlus raw = img;
		return raw;
	}

	public static void main( final String[] args )
	{
		ImageJ ij = new ImageJ();
		ij.launch();
		IJ.openImage( "http://imagej.net/images/blobs.gif" ).show();
		ij.command().run( CellposeAppose.class, true );
	}
}
