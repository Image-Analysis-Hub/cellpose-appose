package fiji.plugin.appose.cellpose;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
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

@Plugin(type = Command.class, headless = true, menuPath = "Help>CellposeAppose")
public class CellposeAppose implements Command
{
	private String mode_3d = "2D+stitch"; // 3d mode
	private double cell_diameter = 30; // cell diameter
	private int cyto_chanel = 1; // cytoplasmic channel to segment
	private int nuc_channel = 0; // nucleus channel if there is one to use
	
	
	//@Parameter( label = "Choose model" )
	private String cp_model = "cyto3"; // cellpose model
	
	/**
	 * Interface to select Cellpose parameters
	 * @return
	 */
	/**public void getParameters( boolean is3d, int multichannel )
	{
		String[] cp_models = {"cyto3", "nuclei"};
		String[] modes_3d = {"2D+stitch", "3D"};
		
		// Creates the interface to choose parameters
		GenericDialog gd = new GenericDialog("Options", IJ.getInstance() );
		Font boldy = new Font("SansSerif", Font.CENTER_BASELINE, 15);
		gd.setFont(boldy);
		gd.addMessage("------ Cellpose parameters ----------------- ");
		gd.addChoice("Choose_model", cp_models, cp_models[0]);
		gd.addNumericField( "Diameter", cell_diameter );
		gd.addToSameRow();
		gd.addMessage("Cell average diameter, in pixels");
		// Get the 3d mode to use
		if ( is3d )
		{
			gd.addChoice("Use 3D mode", modes_3d, modes_3d[0]);
		}
		// Get the channels to use
		if ( multichannel > 1 )
		{
			String[] chanlist = new String[multichannel+1];
			for ( int n=0; n<multichannel; n++ )
				chanlist[n] = ""+(n+1);
			chanlist[multichannel] = "None";
			gd.addChoice( "Cytoplasm_chanel", chanlist, "1" );
			gd.addChoice( "Nucleus_chanel", chanlist, "None" );
			
		}
		
		
		gd.showDialog();
		if (gd.wasCanceled()) return;

		// read the parameters chosen by the user
		String cp_model = gd.getNextChoice();
		cell_diameter = (double) gd.getNextNumber();
		if ( is3d )
		{
			mode_3d = gd.getNextChoice();
		}
		if ( multichannel > 1 )
		{
			cyto_chan = (int) gd.getNextChoice();
			String nuc_chan = gd.getNextChoice();
		}
	}*/
	
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
			
			System.out.println("Model chosen "+cp_model);
			
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
		System.out.println( "This machine os and arch:" );
		System.out.println( "  " + System.getProperty( "os.name" ) );
		System.out.println( "  " + System.getProperty( "os.arch" ) );
		System.out.println();

		/*
		 * For this example we use pixi to create a Python environment with the
		 * necessary dependencies. It is specified with a string that contains a
		 * YAML specification of the environment, similar to what you would put
		 * in an environment.yaml file. You could load it from an existing file,
		 * be here for simplicity it is directly returned as a string. See the
		 * corresponding method.
		 */

		// The pixi environment spec.
		final String cellposeEnv = pixiEnv();
		System.out.println( "The pixi environment specs:" );
		System.out.println( indent( cellposeEnv ) );
		System.out.println();

		/*
		 * The Python script that we want to run. It is specified as a string,
		 * but it could be loaded from an existing .py file. In our case the
		 * script is very simple and has no parameters. We give details on how
		 * to pass input and receive outputs below.
		 */

		// Get the script
		final String script = getScript();
		System.out.println( "The analysis script" );
		System.out.println( indent( script ) );
		System.out.println();

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
			System.out.println( "Starting task" );
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
			final NDArray maskArr = ( NDArray ) task.outputs.get( "rotated" );
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
	private static String pixiEnv()
	{
		String env = "";
		try {
			env = Files.readString(Paths.get("pixi.toml"));
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
	private static String getScript()
	{
		return ""
				+ "from skimage.transform import rotate\n"
				+ "import appose\n"
				+ "\n"
				+ "# The variable 'image' is automatically created by Appose from the \n"
				+ "# input map that we passed when creating the task. It is a shared \n"
				+ "# memory NDArray that can be unwrapped as a NumPy array.\n"
				+ "# Careful: the variable name 'image' MUST be the key that we used in \n"
				+ "# the input map in Java.\n"
				+ "img = image.ndarray()\n"
				+ "\n"
				+ "# Now we have 'img' as a NumPy array.\n"
				+ "\n"
				+ "# Rotate the image by 90 degrees (counter-clockwise)\n"
				+ "rotated_image = rotate(img, angle=90, resize=True)\n"
				+ "\n"
				+ "# Output back to Fiji\n"
				+ "# First we create a NDArray placeholder, of the same type and shape as \n"
				+ "# the image we want to return.\n"
				+ "shared = appose.NDArray(str(rotated_image.dtype), rotated_image.shape)\n"
				+ "\n"
				+ "# Then we fill this placeholder with the data that we want to return.\n"
				+ "shared.ndarray()[:] = rotated_image[:]\n"
				+ "\n"
				+ "# Finally, we put this NDArray in the task outputs with a specific key (here 'rotated'), \n"
				+ "# so that it can be retrieved from Java after the script is done. The key 'rotated' is \n"
				+ "# arbitrary, but it must be the same as the one we use in Java to retrieve the output.\n"
				+ "task.outputs['rotated'] = shared\n";
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
		ImageJ.main( args );
		IJ.openImage( "https://imagej.net/images/FluorescentCells.jpg" ).show();
		final CellposeAppose plugin = new CellposeAppose();
		plugin.run();
	}
}
