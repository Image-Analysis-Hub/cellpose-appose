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

package fiji.plugin.appose.cellpose.cp4;

import static fiji.plugin.appose.ApposeUtils.addROIs;
import static fiji.plugin.appose.ApposeUtils.asCUDA;
import static fiji.plugin.appose.ApposeUtils.convertChannelChoiceToInt;
import static fiji.plugin.appose.ApposeUtils.getChannelChoices;
import static fiji.plugin.appose.ApposeUtils.getCudaVersion;
import static fiji.plugin.appose.ApposeUtils.is3d;

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

import fiji.plugin.appose.cellpose.Cellpose;
import fiji.plugin.appose.cellpose.FijiApposeTaskListener;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.Cellpose3BuiltinModels;
import net.imglib2.cellpose.Cellpose4BuiltinModels;
import net.imglib2.cellpose.Cellpose4Parameters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

@Plugin( type = Command.class, menuPath = "Plugins>Segmentation>Cellpose-SAM..." )
public class CellposeSAMAppose extends DynamicCommand implements Initializable
{
	@Parameter
	private TaskService taskService;
	
	@Parameter
	private PrefService prefService;


	@Parameter(label="", visibility=ItemVisibility.MESSAGE, persist = false)
    private final String messageTitle = "<html>" +
            "<table><tr valign='top'><td>" +
            "<h2>Cell Detection using Cellpose-SAM (v4) brought to you by Appose !</h2>" +
            "See plugin documentation: <a href='https://imagej.net/plugins/fiji-cellpose'>https://imagej.net/plugins/fiji-cellpose</a>" +
            "<br/><br/><a href='https://github.com/mouseland/cellpose'>https://github.com/mouseland/cellpose</a>" +
			" <font face='Courier New' size='5'>&#9829;</font> " +
			"<a href='https://apposed.org/'>https://apposed.org/</a>" +
			"<br/><small>Please cite the Cellpose paper if this tool was useful to you: <a href='https://doi.org/10.1101/2025.04.28.651001'>https://doi.org/10.1101/2025.04.28.651001</a></small>" +
            "</td><td>&nbsp;&nbsp;<img src='"+this.getClass().getResource("/cp_logo.png")+"' width='100' height='100'></img><td>" +
            "</tr></table>" +
            "</html>";

    // ---------

    @Parameter(visibility=ItemVisibility.MESSAGE, label="<html><b>Cellpose Parameters</b></html>", persist = false)
    private final String initMsg = "<html><hr width='100'></html>";

    @Parameter( label = "Cellpose model", description = "Choose CP model to run" )
	private Cellpose4BuiltinModels cp_model = Cellpose4BuiltinModels.CPSAMV2; // cellpose model to use, ignored if custom model path is provided
    
	@Parameter( label = "Path to custom model", description = "Custom model path, overrides the Cellpose model", required = false )
	private String custom_model = ""; // path to custom model, if empty use the selected Cellpose model

	@Parameter( label = "Diameter", min = "0", description = "Average diameter of a cell/nuclei (in pixels)" )
	private int cell_diameter = 30; // cell diameter (in pixels) @StRigaud: is this still used in CP4 ? 

	@Parameter( label = "First channel", choices = { "1", "None" }, description = "First channel index. N/A for none" )
	private String chan0 = "1"; // channel 1, to be merged as RGB for by CP

	@Parameter( label = "Second channel", choices = { "None" }, description = "Second channel index. N/A for none" )
	private String chan1 = "None"; // channel 2, to be merged as RGB for by CP

	@Parameter( label = "Third channel", choices = { "None" }, description = "Third channel index. N/A for none" )
	private String chan2 = "None"; // channel 3, to be merged as RGB for by CP

	@Parameter( label = "Minimum Object Size", min = "0", description = "Minimum object size (in pixels) to keep" )
	private int min_size = 15; // minimum object size (in pixels)

	@Parameter( label = "Normalize Channel Intensity", description = "Normalize intensity on each channels" )
	private Boolean normalize = true; // intensity normalization

	@Parameter( label = "Resample Segmentation", description = "Resample detection to image scale for smoother output" )
	private Boolean resample = true; // resample mask (slower but nicer)

	@Parameter( label = "return ROIs", description = "Return the ROIs (only in 2D)" )
	private Boolean return_ROIs = false; // if true return ROIs (Note: only for 2D image)
    
	// ---------
	
	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><b>Advanced Options</b></html>", persist = false)
    private final String advMsg = "<html><hr width='100'></html>";

	@Parameter( label = "Cell probability threshold", min = "-6.0", max = "6.0", description = "Threshold on cell detection", stepSize = "0.1" )
	private Double cellprob_threshold = 0.0;

	@Parameter( label = "Flows Threshold", min = "0", max = "1", description = "Threshold on flows to detect objects (only for 2D)", stepSize = "0.1" )
	private double flow_threshold = 0.4; // probability threshold on flows

	@Parameter( label = "Tile overlap", min = "0", max = "1", description = "Overlap ratio between tiles", stepSize = "0.1" )
	private double tile_overlap = 0.1; // overlap ration between cellpose tiles

	@Parameter( label="Iterations", min="0", description="Number of iterations for flow computations (niter parameter). Increase it (eg 1000,2000) for elongated shapes" ) 
	private Integer niter = 0; // number of iterations. If 0, put None and use default

	@Parameter( label = "Compute Flows", description = "Compute the segmentation flows output" )
	private Boolean compute_flows = false; // whether to compute flows channel

	// ---------
	@Parameter(visibility=ItemVisibility.MESSAGE, label="<html><b>3D Options</b></html>", persist = false)
    private final String dimMsg = "<html><hr width='100'></html>";

	@Parameter( visibility=ItemVisibility.NORMAL, label = "Mode 3D", choices = { "None", "2D+stitch", "3D" }, description = "How is cellpose is processing the image if it is 3D")
	private String mode_3d = "None"; // mode 3D of CP to use, only for 3D image

	private boolean is3D = false;

	@Parameter( visibility=ItemVisibility.NORMAL, label="Stitch threshold", min="0.0", max="1.0", description="2D+stitch mode only: IOU threshold to stitch labels together along the Z-axis"  )
	private Double stitch_threshold = 0.1; 
	
	@Parameter( visibility=ItemVisibility.NORMAL, label="Flow3d smooth", min="0", description="3D mode only: Gaussian smoothing sigma applied on flows." ) 
	private Integer flow3d_smooth = 0; // gaussian smooth of 3D flows
	

	// ---------
	
	@Parameter(visibility=ItemVisibility.MESSAGE, label=" ", persist = false)
    private final String sysMsg = "<html><hr width='100'></html>";

	@Parameter(visibility=ItemVisibility.MESSAGE, label=" ", persist = false)
	private String sysInfo = "";

	@Parameter(label="Torch version", choices = { "cpu", "cu126", "cu130" }, description = "Control which torch/cuda version to use.")
	private String torchVersion = "cpu";

	@Parameter( label = "use GPU", description = "Run on GPU if available" )
	private Boolean useGPU = true;

	// ---------
	
	private boolean use3d = false;

	private double anisotropy = 1.0;


	/*
	 * Initialize the plugin.
	 * This method is called when the plugin is loaded, and it is used to initialize the plugin parameters.
	 * Check for Image correctness, manage parameters visibility and choices based on the image properties (2D vs 3D, number of channels, etc).
	 */
	@Override
	public void initialize()
	{
		// prefService.clear( this.getClass() ); 

		// Grab the current image (last touched image in Fiji)
		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( imp == null )
		{
			// ToDo: Find a cleaner way to exit, the "return" still trigger the
			// plugin interface, I needed to throw an exception for the process to stop.
			IJ.error( "No image available to process" );
			throw new RuntimeException( "No image available to process" );
		}
		
		is3D = is3d( imp );

		final List< String > channelChoices = getChannelChoices( imp, false );

		// Set the max possible value of channels based on image dimension
		final MutableModuleItem< String > c0Item =
				getInfo().getMutableInput( "chan0", String.class );
		c0Item.setChoices( channelChoices );
		
		final MutableModuleItem< String > c1Item =
				getInfo().getMutableInput( "chan1", String.class );
		c1Item.setChoices( channelChoices );

		final MutableModuleItem< String > c2Item =
				getInfo().getMutableInput( "chan2", String.class );
		c2Item.setChoices( channelChoices );

		// if the image is 3D, update the GUI to display the 3D options, otherwise fix them
		if ( is3D )
		{
			final List< String > modeChoices = Arrays.asList( "2D+stitch", "3D" );
			final MutableModuleItem< String > mode3dItem =
					getInfo().getMutableInput( "mode_3d", String.class );
			mode3dItem.setChoices( modeChoices );

			final MutableModuleItem< Integer > flowItem = 
					getInfo().getMutableInput( "flow3d_smooth", Integer.class );
			flowItem.setMinimumValue( 0 );
			
			final MutableModuleItem< Double > stitchItem = 
					getInfo().getMutableInput( "stitch_threshold", Double.class );
			stitchItem.setMinimumValue( 0.0 );
			stitchItem.setMaximumValue( 1.0 );
			stitchItem.setStepSize( 0.05 );		

		}
		else
		{
			// List< String > modeChoices = Arrays.asList( "None" );
			final MutableModuleItem< String > mode3dItem =
					getInfo().getMutableInput( "mode_3d", String.class );
			// mode3dItem.setChoices( modeChoices );
			setInput( "mode_3d", "None" );
			
			final MutableModuleItem< Integer > flowItem = 
					getInfo().getMutableInput( "flow3d_smooth", Integer.class );
			setInput( "flow3d_smooth", "0" );
			
			final MutableModuleItem< Double > stitchItem = 
					getInfo().getMutableInput( "stitch_threshold", Double.class );
			setInput( "stitch_threshold", "0.1" );
		}

		if ( !asCUDA() )
		{
			torchVersion = "cpu";
			final MutableModuleItem< String > torchItem =
					getInfo().getMutableInput( "torchVersion", String.class );
			torchItem.setChoices( List.of( "cpu" ) );
			torchItem.setDefaultValue( "cpu" );
			setInput( "torchVersion", "cpu" );
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

		// Grab the current image.
		final ImagePlus imp = WindowManager.getCurrentImage();
		try
		{
			// Get the parameters based on the image properties
			final boolean is3D = is3d( imp );

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
		
		// Check if the image is RGB
		if ( imp.isRGB() )
		{
			IJ.error( "Image is RGB, which is not handled. Change image type in Image>Color>Make Composite and start again" );
			return;
		}
				
		try
		{
			final Cellpose4Parameters params = Cellpose4Parameters.builder()
					.model(cp_model)
					.customModel(custom_model)
					.diameter(cell_diameter)
					.chan0( convertChannelChoiceToInt( chan0, false ) )
					.chan1( convertChannelChoiceToInt( chan1, false ) )
					.chan2( convertChannelChoiceToInt( chan2, false ) )
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
					.useGpu(useGPU)
					.torchVersion(torchVersion)
					.useGpu( useGPU )
					.build();

			final ApposeTaskListener listener = new FijiApposeTaskListener();
			final ImagePlus[] outputs = Cellpose.cellpose4( imp, params, listener );
			
			final ImagePlus labels = outputs[ 0 ];
			if ( return_ROIs )
			{
				addROIs( labels, "Cellpose-4", Color.YELLOW );
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
