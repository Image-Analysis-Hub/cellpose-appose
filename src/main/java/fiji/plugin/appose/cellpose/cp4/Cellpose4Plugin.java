package fiji.plugin.appose.cellpose.cp4;

import static fiji.plugin.appose.ApposeUtils.addROIs;

import java.awt.Color;
import java.io.IOException;

import org.apposed.appose.BuildException;
import org.scijava.ui.config.fiji.ConfigFijiPlugin;
import org.scijava.ui.config.fiji.listeners.FijiApposeProgressListener;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;

import fiji.plugin.appose.cellpose.Cellpose;
import fiji.plugin.appose.cellpose.CellposeApposeListener;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import net.imglib2.cellpose.Cellpose4Parameters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class Cellpose4Plugin extends ConfigFijiPlugin< Cellpose4Config >
{

	@Override
	public void run( final Progress progress ) throws Exception
	{
		process( progress );
		super.run( progress );
	}

	public < T extends RealType< T > & NativeType< T > > void process( final Progress progress ) throws IOException, BuildException
	{
		// Print os and arch info
		progress.message( "Starting process..." );

		final ImagePlus imp = getImagePlus();
		final Cellpose4Config config = getConfig();

		try
		{
			// Convert config to Cellpose parameters.
			final Cellpose4Parameters params = toParams( getConfig() );
					
			// Adapt listener.
			final FijiApposeProgressListener l = new FijiApposeProgressListener( progress, config.getName() );
			final CellposeApposeListener listener = CellposeApposeListener.of( l );

			// Exec.
			final ImagePlus[] outputs = Cellpose.cellpose4( imp, params, listener );

			// Unwrap the outputs and show them.
			final ImagePlus labels = outputs[ 0 ];
			if ( config.exportROIs().getValue() && imp.getNSlices() == 1 )
			{
				addROIs( labels, "Cellpose-3", Color.YELLOW );
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

	private Cellpose4Parameters toParams( final Cellpose4Config config )
	{
		final String selection = config.builtinOrCustom().getSelection().getKey();
		final boolean isBuiltin = selection.equals( "BUILTIN_MODEL" );

		final Cellpose4Parameters params = Cellpose4Parameters.builder()
				.model( isBuiltin ? config.builtinModel().getValue() : null )
				.customModel( isBuiltin ? null :  config.customModel().getValue() )
				.diameter( config.diameter().getValue() )
				.chan0( config.chan1().getValue() )
				.chan1( config.chan2().getValue() )
				.chan2( config.chan3().getValue() )
				.minSize( config.minSize().getValue() )
				.normalize( config.normalize().getValue() )
				.resample( true ) // Must be true here, as we expect the output to have the same size as the input.
				.cellProbThreshold( config.flowThreshold().getValue() )
				.flowThreshold( config.flowThreshold().getValue() )
				.tileOverlap( config.tileOverlap().getValue() )
				.computeFlows( config.exportFlows().getValue() )
				.do3D( config.mode3D().getValue() )
				.stitchThreshold( config.stitchThreshold().getValue() )
				.flow3dSmooth( config.flow3DSmooth().getValue() )
				.nIter( config.nIter().getValue() )
				.torchVersion( config.torchVersion().getValue() )
				.useGpu( config.useGpu().getValue() )
				.build();
		return params;
	}

	@Override
	protected Cellpose4Config createConfig( final ImagePlus imp )
	{
		final int nChannels = imp.getNChannels();
		final double pixelSize = imp.getCalibration().pixelWidth;
		final String units = imp.getCalibration().getUnit();
		return new Cellpose4Config( nChannels, pixelSize, units );
	}
}
