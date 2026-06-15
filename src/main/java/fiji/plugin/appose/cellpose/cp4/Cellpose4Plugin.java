package fiji.plugin.appose.cellpose.cp4;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.cellpose.Cellpose;
import fiji.plugin.appose.cellpose.CellposeAbstractPlugin;
import fiji.plugin.appose.cellpose.CellposeApposeListener;
import ij.ImagePlus;
import net.imglib2.cellpose.Cellpose4BuiltinModels;
import net.imglib2.cellpose.Cellpose4Parameters;

public class Cellpose4Plugin extends CellposeAbstractPlugin< Cellpose4Config, Cellpose4BuiltinModels, Cellpose4Parameters >
{

	@Override
	protected Cellpose4Parameters toParams( final Cellpose4Config config )
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

	@Override
	protected ImagePlus[] execCellpose( final ImagePlus imp, final Cellpose4Parameters params, final CellposeApposeListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		return Cellpose.cellpose4( imp, params, listener );
	}
}
