package fiji.plugin.appose.cellpose.cp3;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.cellpose.Cellpose;
import fiji.plugin.appose.cellpose.CellposeAbstractPlugin;
import fiji.plugin.appose.cellpose.CellposeApposeListener;
import ij.ImagePlus;
import net.imglib2.cellpose.Cellpose3BuiltinModels;
import net.imglib2.cellpose.Cellpose3Parameters;

public class Cellpose3Plugin extends CellposeAbstractPlugin< Cellpose3Config, Cellpose3BuiltinModels, Cellpose3Parameters >
{

	@Override
	protected Cellpose3Parameters toParams( final Cellpose3Config config )
	{
		final List< Integer > channels = Arrays.asList(
				config.chan1().getValue(),
				config.chan2().getValue() );

		final String selection = config.builtinOrCustom().getSelection().getKey();
		final boolean isBuiltin = selection.equals( "BUILTIN_MODEL" );

		final Cellpose3Parameters params = Cellpose3Parameters.builder()
				.model( isBuiltin ? config.builtinModel().getValue() : null )
				.customModel( isBuiltin ? null :  config.customModel().getValue() )
				.diameter( config.diameter().getValue() )
				.channels( channels )
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
	protected Cellpose3Config createConfig( final ImagePlus imp )
	{
		final int nChannels = imp.getNChannels();
		final double pixelSize = imp.getCalibration().pixelWidth;
		final String units = imp.getCalibration().getUnit();
		return new Cellpose3Config( nChannels , pixelSize , units  );
	}

	@Override
	protected ImagePlus[] execCellpose( final ImagePlus imp, final Cellpose3Parameters params, final CellposeApposeListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		return Cellpose.cellpose3( imp, params, listener );
	}
}
