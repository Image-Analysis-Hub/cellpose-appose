
package fiji.plugin.appose.cellpose.cp3;

import static fiji.plugin.appose.ApposeUtils.getCudaVersion;


import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apposed.appose.BuildException;
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
import fiji.plugin.appose.cellpose.Cellpose;
import fiji.plugin.appose.cellpose.Cellpose3BuiltinModels;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
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

	@Parameter( label = "Cellpose model", description = "Choose CP model to run" )
	private Cellpose3BuiltinModels cp_model = Cellpose3BuiltinModels.CYTO3; // cellpose model to use, ignored if custom model path is provided

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
	
	@Parameter(visibility=ItemVisibility.MESSAGE, label=" ")
    private final String sysMsg = "<html><hr width='100'></html>";

	@Parameter(visibility=ItemVisibility.MESSAGE, label=" ")
	private String sysInfo = "";

	// ---------
	
	private boolean use3d = false;

	private double anisotropy = 1.0;

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
		// set the default value, otherwise it gets to -6
		setInput( "cellprob_threshold", 0.0) ;
		// Grab the current image (last touched image in Fiji)
		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( imp == null )
		{
			// ToDo: Find a cleaner way to exit, the "return" still trigger the
			// plugin interface, I needed to throw an exception for the process to stop.
			IJ.error( "No image available to process" );
			throw new RuntimeException( "No image available to process" );
		}
		
		if ( imp.getNSlices() > 1 && imp.getNFrames() > 1 )
		{
			throw new RuntimeException( "5D images are not supported, please select a single time point or a single Z-slice to process." );
		}

		is3D = ApposeUtils.is3d( imp );

		final List< String > channelChoices = ApposeUtils.getChannelChoices( imp, true );

		// Set the max possible value of channels based on image dimension
		final MutableModuleItem< String > cytoItem =
				getInfo().getMutableInput( "cyto_channel", String.class );
		cytoItem.setChoices( channelChoices );
		cytoItem.setDefaultValue( "1" ); // Default, cyto channel selected only
		 setInput( "cyto_channel", channelChoices.get(0)) ;

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

		// display system info (OS, device) for user awareness
		sysInfo = "<center>OS: " + System.getProperty( "os.name" ) + " - " + System.getProperty( "os.arch" ) + " | Device: " + ( getCudaVersion() != null ? "CUDA " + getCudaVersion() : "CPU" ) + "</center>";
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

		if ( imp.getNSlices() > 1 && imp.getNFrames() > 1 )
		{
			throw new RuntimeException( "5D images are not supported, please select a single time point or a single Z-slice to process." );
		}

		try
		{
			final List<Integer> channels = Arrays.asList(
				ApposeUtils.convertChannelChoiceToInt( cyto_channel, true ), 
				ApposeUtils.convertChannelChoiceToInt( nuclei_channel, true ));

			final Cellpose3Parameters params = Cellpose3Parameters.builder()
					.model(cp_model)
					.customModel(custom_model)
					.diameter(cell_diameter)
					.channels( channels )
					.minSize( min_size )
					.normalize( normalize )
					.resample( resample )
					.cellProbThreshold( cellprob_threshold )
					.flowThreshold( flow_threshold )
					.tileOverlap( tile_overlap )
					.computeFlows( compute_flows )
					.do3D( use3d )
					.stitchThreshold( stitch_threshold )
					.flow3dSmooth( flow3d_smooth )
					.nIter( niter )
					.build();

			final ImagePlus[] outputs = Cellpose.cellpose3( imp, params );
			
			final ImagePlus labels = outputs[ 0 ];
			if ( return_ROIs )
			{
				ApposeUtils.addROIs( labels, "Cellpose-3", Color.YELLOW );
				RoiManager.getInstance2().runCommand( "Show All" );
			}
			labels.show();

			if ( compute_flows && outputs.length > 1 )
			{
				final ImagePlus flows = outputs[ 1 ];
				flows.show();
			}
		}
		catch ( final Exception e )
		{
			IJ.handleException( e );
		}	
	}
}
