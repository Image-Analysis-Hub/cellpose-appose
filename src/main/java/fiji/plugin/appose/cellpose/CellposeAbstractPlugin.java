package fiji.plugin.appose.cellpose;

import static fiji.plugin.appose.ApposeUtils.addROIs;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;
import org.scijava.Cancelable;
import org.scijava.command.Previewable;
import org.scijava.ui.config.fiji.ConfigFijiPlugin;
import org.scijava.ui.config.fiji.listeners.FijiApposeProgressListener;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;
import net.imglib2.cellpose.CellposeParameters;

public abstract class CellposeAbstractPlugin< 
		C extends CellposeBaseConfig< CBM >, 
		CBM extends Enum< CBM >,
		CP extends CellposeParameters > extends ConfigFijiPlugin< C >
	implements Previewable,Cancelable
{

	private Future< ImagePlus[] > cellposeTask;

	private String cancelReason;

	@Override
	public void run( final Progress progress ) throws Exception
	{
		process( progress );
		super.run( progress );
	}

	/**
	 * Process the image that was active then plugin was launched, with the
	 * current configuration.
	 * 
	 * @param progress
	 *            the progress to report to
	 * @throws IOException
	 * @throws BuildException
	 */
	protected void process( final Progress progress ) throws IOException, BuildException
	{
		progress.message( "Starting process..." );
		final ImagePlus imp = getImagePlus();
		final Roi roi = imp.getRoi();
		process( imp, progress, 0 );
		imp.setRoi( roi );
	}

	/**
	 * Processes the specified image with the current configuration. The tOrigin
	 * is used to translate the ROIs in time in the case of a preview.
	 * 
	 * @param imp
	 *            the image to process
	 * @param progress
	 *            the progress to report to
	 * @param tOrigin
	 *            the time origin to translate the ROIs in time
	 * @throws IOException
	 * @throws BuildException
	 */
	protected void process( final ImagePlus imp, final Progress progress, final int tOrigin ) throws IOException, BuildException
	{
		final C config = getConfig();
		try
		{
			// Convert config to Cellpose parameters.
			final CP params = toParams( getConfig() );

			// Adapt listener.
			final FijiApposeProgressListener l = new FijiApposeProgressListener( progress, config.getName() );
			final CellposeApposeListener listener = CellposeApposeListener.of( l );

			// Exec.

			// Run execCellpose in a separate thread, and gets its outputs.
			this.cellposeTask = Executors.newSingleThreadExecutor().submit( () -> execCellpose( imp, params, listener ) );
			ImagePlus[] outputs = null;
			try
			{
				outputs = cellposeTask.get();
			}
			catch ( final CancellationException e )
			{
				// Do nothing, it's ok that the user canceled the task.
			}
			if ( outputs == null )
			{
				System.out.println( "CellposeAbstractPlugin.process(): got no output from execCellpose()" );
				return; // We got no output
			}

			// Unwrap the outputs and show them.
			final ImagePlus labels = outputs[ 0 ];
			if ( config.exportROIs().getValue() && imp.getNSlices() == 1 )
			{
				final boolean multipleChannels = imp.getNChannels() > 1;
				addROIs( labels, config.getName(), Color.YELLOW, tOrigin, multipleChannels );
				RoiManager.getInstance2().runCommand( "Show All" );
			}
			if ( config.exportLabels().getValue() )
				labels.show();
			if ( config.exportFlows().getValue() && outputs.length > 1 )
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

	protected abstract ImagePlus[] execCellpose( final ImagePlus imp, final CP params, final CellposeApposeListener listener ) throws BuildException, IOException, InterruptedException, TaskException;

	protected abstract CP toParams( final C config );

	// Cancelable

	@Override
	public void cancel( final String cancelReason )
	{
		this.cancelReason = cancelReason;
		if ( cellposeTask != null )
			cellposeTask.cancel( false );
	}

	@Override
	public boolean isCanceled()
	{
		return cellposeTask != null && cellposeTask.isCancelled();
	}

	@Override
	public String getCancelReason()
	{
		return cancelReason;
	}

	// Previewable

	@Override
	public void preview()
	{
		final ImagePlus imp = getImagePlus();
		final Roi roi = imp.getRoi();
		final Duplicator dup = new Duplicator();
		final int z = imp.getSlice();
		final int t = imp.getFrame();
		final ImagePlus crop = dup.run( imp, 1, imp.getNChannels(), z, z, t, t );
		// Translate origin so that the ROIs are correctly positioned.
		if ( roi != null )
		{
			crop.getCalibration().xOrigin = roi.getBounds().x;
			crop.getCalibration().yOrigin = roi.getBounds().y;
			final Rectangle bounds = roi.getBounds();
			final Roi clone = ( Roi ) roi.clone();
			clone.translate( -bounds.x, -bounds.y );
			crop.setRoi( clone );
			// We need the ROI so that the outside of it are properly masked.
		}

		final Progress progress = new IJProgress();
		progress.message( "Starting process..." );
		final int tOrigin = t - 1;
		try
		{
			process( crop, progress, tOrigin );
		}
		catch ( IOException | BuildException e )
		{
			IJ.handleException( e );
			e.printStackTrace();
		}
		finally
		{
			crop.changes = false;
			crop.close();
			imp.setRoi( roi );
		}
	}

	@Override
	public void cancel()
	{
		// We don't cancel preview.
	}
}
