package fiji.plugin.appose.cellpose.cp3;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import fiji.plugin.appose.ImageAxisInfo;
import fiji.plugin.appose.cellpose.Cellpose3BuiltinModels;
import fiji.plugin.appose.cellpose.CellposeParameters;
import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class Cellpose3Parameters extends CellposeParameters
{

	public final Cellpose3BuiltinModels buitInModel;

	public final List< Integer > channels;

	private Cellpose3Parameters(
			final Cellpose3BuiltinModels buitInModel,
			final List< Integer > channels,
			final String customModel,
			final double diameter,
			final boolean do3D,
			final boolean normalize,
			final double flowThreshold,
			final double cellProbThreshold,
			final boolean useGpu,
			final double minSize,
			final double anisotropy,
			final double stitchThreshold,
			final boolean resample,
			final double tileOverlap,
			final boolean computeFlows,
			final int flow3dSmooth,
			final int nIter )
	{
		super(
				customModel, diameter, do3D, normalize, flowThreshold,
				cellProbThreshold, useGpu, minSize, anisotropy,
				stitchThreshold, resample, tileOverlap, computeFlows,
				flow3dSmooth, nIter );
		this.buitInModel = buitInModel;
		this.channels = channels;
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > Map< String, Object > toApposeMap( final ImgPlus< T > img )
	{
		final Map< String, Object > inputs = super.toApposeMap( img );
		final boolean isBuiltInModel = customModel == null || customModel.equals( "" );
		inputs.put( "model_name", isBuiltInModel ? buitInModel.modelName() : null );
		inputs.put( "cell_channel", channels.get( 0 ) );
		inputs.put( "nuclei_channel", channels.get( 1 ) );

		final ImageAxisInfo axisInfo = ImageAxisInfo.fromImgPlusToPython( img );
		inputs.put( "t_axis", axisInfo.time_axis );
		inputs.put( "z_axis", axisInfo.z_axis );
		inputs.put( "channel_axis", axisInfo.channel_axis );

		return inputs;
	}

	// Static builder method for convenience
	public static Builder builder()
	{
		return new Builder();
	}

	// Builder class for fluent construction
	public static class Builder extends CellposeParameters.Builder< Builder >
	{
		private Cellpose3BuiltinModels model = Cellpose3BuiltinModels.CYTO3;

		private List< Integer > channels = List.of( 0, 0 );

		public Builder model( final Cellpose3BuiltinModels model )
		{
			this.model = model;
			return this;
		}

		public Builder channels( final List< Integer > channels )
		{
			this.channels = channels;
			return this;
		}

		public Builder channels( final Integer channel1, final Integer channel2 )
		{
			this.channels = Arrays.asList( channel1, channel2 );
			return this;
		}
		
		public Builder channels( final int channel1, final int channel2 )
		{
			this.channels = Arrays.asList( channel1, channel2 );
			return this;
		}

		@Override
		public Cellpose3Parameters build()
		{
			return new Cellpose3Parameters(
					model, channels, customModel, diameter, do3D, normalize,
					flowThreshold, cellProbThreshold, useGpu, minSize,
					anisotropy, stitchThreshold, resample, tileOverlap,
					computeFlows, flow3dSmooth, nIter );
		}
	}

	// Default parameters for common use cases
	public static Cellpose3Parameters defaultCyto3Parameters()
	{
		return builder()
				.model( Cellpose3BuiltinModels.CYTO3 )
				.channels( 0, 0 )
				.diameter( 30.0 )
				.build();
	}

	public static Cellpose3Parameters defaultCyto2Parameters()
	{
		return builder()
				.model( Cellpose3BuiltinModels.CYTO2 )
				.channels( 0, 0 )
				.diameter( 30.0 )
				.build();
	}

	public static Cellpose3Parameters defaultNucleiParameters()
	{
		return builder()
				.model( Cellpose3BuiltinModels.NUCLEI )
				.channels( 0, 0 )
				.diameter( 17.0 )
				.build();
	}
}