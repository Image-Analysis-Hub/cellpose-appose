package fiji.plugin.appose.cellpose.cp4;

import java.util.Map;

import fiji.plugin.appose.ImageAxisInfo;
import fiji.plugin.appose.cellpose.CellposeParameters;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class Cellpose4Parameters extends CellposeParameters
{

	public final int chan0;

	public final int chan1;

	public final int chan2;

	private Cellpose4Parameters(
			final int chan0,
			final int chan1,
			final int chan2,
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
		this.chan0 = chan0;
		this.chan1 = chan1;
		this.chan2 = chan2;
	}

	/**
	 * Creates a parameters map suitable for passing to Appose, using the
	 * specified image as input, and the parameter values stored in this object.
	 * 
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param img
	 *            the input image.
	 * @return a new map.
	 */
	@Override
	public < T extends RealType< T > & NativeType< T > > Map< String, Object > toApposeMap( final ImgPlus< T > img )
	{
		final int cAxis = img.dimensionIndex( Axes.CHANNEL );
		final int nChannels = ( int ) ( ( cAxis >= 0 ) ? img.dimension( cAxis ) : 1 );

		final Map< String, Object > inputs = super.toApposeMap( img );
		inputs.put( "n_channels", nChannels );
		inputs.put( "chan0", ( chan0 < 0 ) ? null : chan0 );
		inputs.put( "chan1", ( chan1 < 0 ) ? null : chan1 );
		inputs.put( "chan2", ( chan2 < 0 ) ? null : chan2 );

		final ImageAxisInfo axisInfo = ImageAxisInfo.fromImgPlus( img );
		inputs.put( "z_axis", axisInfo.z_axis );
		inputs.put( "channel_axis", axisInfo.channel_axis );
		inputs.put( "time_axis", axisInfo.time_axis );

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

		private int chan0 = -1;

		private int chan1 = -1;

		private int chan2 = -1;

		public Builder chan0( final int chan0 )
		{
			this.chan0 = chan0;
			return this;
		}

		public Builder chan1( final int chan1 )
		{
			this.chan1 = chan1;
			return this;
		}

		public Builder chan2( final int chan2 )
		{
			this.chan2 = chan2;
			return this;
		}

		@Override
		public Cellpose4Parameters build()
		{
			return new Cellpose4Parameters(
					chan0, chan1, chan2, customModel, diameter, do3D, normalize,
					flowThreshold, cellProbThreshold, useGpu, minSize,
					anisotropy, stitchThreshold, resample, tileOverlap,
					computeFlows, flow3dSmooth, nIter );
		}
	}

	public static Cellpose4Parameters defaultParameters()
	{
		return builder().build();
	}
}
