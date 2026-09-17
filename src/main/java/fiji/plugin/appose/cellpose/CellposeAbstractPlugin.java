package fiji.plugin.appose.cellpose;


import static fiji.plugin.appose.ApposeUtils.getAxisInfo;
import static fiji.plugin.appose.ApposeUtils.rawWraps;

import org.scijava.command.Previewable;
import org.scijava.ui.config.fiji.ConfigFijiPluginPreviewable;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame;

import fiji.plugin.appose.listeners.FijiApposeProgressListener;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import net.imagej.ImgPlus;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.cellpose.CellposeOutput;
import net.imglib2.cellpose.CellposeParameters;
import net.imglib2.cellpose.CellposeRunner;
import net.imglib2.cellpose.CellposeRunnerWrapper;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public abstract class CellposeAbstractPlugin< 
		C extends CellposeBaseConfig< CBM >, 
		CBM extends Enum< CBM >,
		CP extends CellposeParameters > extends ConfigFijiPluginPreviewable< C >
	implements Previewable
{

	private CellposeRunner< CP > runner;

	private String previousTorchVersion;

	protected FijiApposeProgressListener listener;

	@SuppressWarnings( "unchecked" )
	@Override
	protected void process( final ImagePlus input, final int tOrigin )
	{
		progress.clear();
		final long startTime = System.currentTimeMillis();
		// Store the ROI in the source image for later.
		final Roi roi = imp.getRoi();

		try
		{
			// Convert config to Cellpose parameters.
			final CP params = toParams( config );

			// Create the runner if it doesn't exist or recreate it.
			if ( runner == null || !params.torchVersion.equals( previousTorchVersion ) )
			{
				listener = new FijiApposeProgressListener( progress, config.getName() );
				runner = createRunner( params.torchVersion );
				runner.init();
				previousTorchVersion = params.torchVersion;
			}

			// ROI in the input. Might be different from the roi in the source
			// image, if this is a preview.
			Roi initialRoi = input.getRoi();
			if ( initialRoi != null )
				initialRoi = ( Roi ) initialRoi.clone();

			// Wrap input.
			@SuppressWarnings( "rawtypes" )
			final ImgPlus img = rawWraps( input );
			final AxisInfo inputAxes = getAxisInfo( img );

			// Exec.
			final long nt = inputAxes.nTimePoints( img );
			final long nz = inputAxes.nZ( img );
			final CellposeOutput< UnsignedShortType > oo;
			final UnsignedShortType outputType = new UnsignedShortType();
			if ( nt > 1 && nz > 1 )
			{
				// Do we have a 5D image? If yes we process time-point by
				// time-point.
				final CellposeRunnerWrapper< CP > wrapper = new CellposeRunnerWrapper<>( runner, d -> progress.set( d ) );
				oo = wrapper.run( img, inputAxes, outputType, params );
			}
			else
			{
				// Otherwise process in one go.
				runner.setInput( img, inputAxes, outputType );
				runner.run( params );
				oo = runner.getOutput();
			}

			final long endTime1 = System.currentTimeMillis();
			progress.message( String.format( "Cellpose done in %.1f seconds. Postprocessing outputs...",
					( endTime1 - startTime ) / 1000. ) );

			// To ImagePlus.
			final ImagePlus[] outputs = Cellpose.toImp( oo );

			// Post-process the outputs.
			for ( final ImagePlus out : outputs )
				postProcessOuput( input, out );

			// Unwrap the outputs and show them.
			final ImagePlus labels = outputs[ 0 ];
			if ( config.exportROIs().getValue() )
				toROIs( input, labels, config.getName(), tOrigin );
			final String suffix = config.getName().replace( " ", "" );
			if ( config.exportLabels().getValue() )
			{
				labels.setTitle( imp.getTitle() + "_" + suffix );
				labels.show();
			}
			if ( config.exportFlows().getValue() && outputs.length > 1 )
			{
				final ImagePlus flows = outputs[ 1 ];
				flows.setTitle( imp.getTitle() + "_flows_" + suffix );
				flows.show();
			}
		}
		catch ( final Exception e )
		{
			IJ.handleException( e );
		}
		finally
		{
			// Restore the ROI in the source image.
			imp.setRoi( roi );
			progress.clear();
			final long endTime2 = System.currentTimeMillis();
			progress.message( String.format( "Done in %.1f seconds.", ( endTime2 - startTime ) / 1000. ) );
		}
	}

	protected abstract CellposeRunner< CP > createRunner( String torchVersion );

	protected abstract CP toParams( final C config );

	/**
	 * Adds a hook the UI, so that we close the {@link #runner} when the UI is
	 * closed.
	 */
	@Override
	protected ConfigFrame showUI()
	{
		final ConfigFrame ui = super.showUI();
		ui.addWindowListener( new java.awt.event.WindowAdapter()
		{
			@Override
			public void windowClosed( final java.awt.event.WindowEvent e )
			{
				if ( runner != null )
				{
					runner.close();
					runner = null;
					listener.close();
					listener = null;
				}
			}
		} );
		return ui;
	}
}
