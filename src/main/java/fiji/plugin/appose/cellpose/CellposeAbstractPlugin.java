package fiji.plugin.appose.cellpose;

import static fiji.plugin.appose.ApposeUtils.addROIs;

import java.awt.Color;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;
import org.scijava.Cancelable;
import org.scijava.ui.config.fiji.ConfigFijiPlugin;
import org.scijava.ui.config.fiji.listeners.FijiApposeProgressListener;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import net.imglib2.cellpose.CellposeParameters;

public abstract class CellposeAbstractPlugin< 
		C extends CellposeBaseConfig< CBM >, 
		CBM extends Enum< CBM >,
		CP extends CellposeParameters > extends ConfigFijiPlugin< C > implements Cancelable
{

	private Future< ImagePlus[] > cellposeTask;

	private String cancelReason;

	@Override
	public void run( final Progress progress ) throws Exception
	{
		process( progress );
		super.run( progress );
	}

	public void process( final Progress progress ) throws IOException, BuildException
	{
		// Print os and arch info
		progress.message( "Starting process..." );

		final ImagePlus imp = getImagePlus();
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
				addROIs( labels, config.getName(), Color.YELLOW, multipleChannels );
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
}
