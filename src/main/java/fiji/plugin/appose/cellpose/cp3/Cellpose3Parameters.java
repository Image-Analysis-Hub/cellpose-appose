package fiji.plugin.appose.cellpose.cp3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fiji.plugin.appose.ImageAxisInfo;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Data class to hold parameters for Cellpose 3 segmentation.
 */
public record Cellpose3Parameters(
		// Core parameters
		Cellpose3Model buitInModel,
		String customModel,
		double diameter,
		boolean do3D,

		// Channel specifications
		List< Integer > channels,
		boolean normalize,

		// Advanced parameters
		double flowThreshold,
		double cellProbThreshold,
		boolean useGpu,
		double minSize,

		// Optional parameters for 3D stitching
		double anisotropy,
		double stitchThreshold,

		// More advanced parameters
		boolean resample,
		double tileOverlap,
		boolean computeFlows,
		int flow3dSmooth,
		int nIter )
{
	// Default constructor with minimal validation
	public Cellpose3Parameters
	{
		// Validate channels
		if ( channels == null || channels.size() != 2 )
			throw new IllegalArgumentException( "Channels must be a list of 2 integers" );

		// Validate diameter
		if ( diameter < 0 )
		{ // 0 is allowed for auto-diameter
			throw new IllegalArgumentException( "Diameter must be positive or 0 for auto-detection" );
		}
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
	public < T extends RealType< T > & NativeType< T > > Map< String, Object > toApposeMap( final ImgPlus< T > img )
	{
		final ImageAxisInfo axisInfo = ImageAxisInfo.fromImgPlus( img );

		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "use_3D", do3D() );
		// return null if custom model
		final String customModel = customModel();
		final boolean isBuiltInModel = customModel == null || customModel.equals( "" );
		inputs.put( "model", isBuiltInModel ? buitInModel().modelName() : null );
		inputs.put( "custom_model", isBuiltInModel ? null : customModel );
		inputs.put( "diameter", diameter() );
		inputs.put( "cell_channel", channels().get( 0 ) );
		inputs.put( "nuclei_channel", channels().get( 1 ) );
		inputs.put( "t_axis", axisInfo.time_axis );
		inputs.put( "stitch_threshold", stitchThreshold() );
		inputs.put( "z_axis", axisInfo.z_axis );
		inputs.put( "anisotropy", anisotropy() );
		inputs.put( "compute_flows", computeFlows() );
		inputs.put( "resample", resample() );
		inputs.put( "normalize", normalize() );
		inputs.put( "flow_threshold", flowThreshold() );
		inputs.put( "cellprob_threshold", cellProbThreshold() );
		inputs.put( "min_size", minSize() );
		inputs.put( "tile_overlap", tileOverlap() );
		inputs.put( "flow3D_smooth", flow3dSmooth() );
		inputs.put( "niter", nIter() <= 0 ? null : nIter() );
		return inputs;
	}

	// Static builder method for convenience
	public static Builder builder()
	{
		return new Builder();
	}

	// Builder class for fluent construction
	public static class Builder
	{
		private Cellpose3Model model = Cellpose3Model.CYTO3;

		private String customModel = null;

		private double diameter = 30.0;

		private boolean do3D = false;

		private List< Integer > channels = List.of( 0, 0 );

		private boolean normalize = true;

		private double flowThreshold = 0.4;

		private double cellProbThreshold = 0.0;

		private boolean useGpu = true;

		private double minSize = 15.;

		private double anisotropy = 1.;

		private double stitchThreshold = 0.;

		private boolean resample = true;

		private double tileOverlap = 0.1;

		private boolean computeFlows = false;

		private int flow3dSmooth = 0;

		private int nIter = 0;

		public Builder model( final Cellpose3Model model )
		{
			this.model = model;
			return this;
		}

		public Builder customModel( final String customModel )
		{
			this.customModel = customModel;
			return this;
		}

		public Builder diameter( final double diameter )
		{
			this.diameter = diameter;
			return this;
		}

		public Builder do3D( final boolean do3D )
		{
			this.do3D = do3D;
			return this;
		}

		public Builder channels( final List< Integer > channels )
		{
			this.channels = channels;
			return this;
		}

		public Builder channels( final int channel1, final int channel2 )
		{
			this.channels = List.of( channel1, channel2 );
			return this;
		}

		public Builder normalize( final boolean normalize )
		{
			this.normalize = normalize;
			return this;
		}

		public Builder flowThreshold( final double flowThreshold )
		{
			this.flowThreshold = flowThreshold;
			return this;
		}

		public Builder cellProbThreshold( final double cellProbThreshold )
		{
			this.cellProbThreshold = cellProbThreshold;
			return this;
		}

		public Builder useGpu( final boolean useGpu )
		{
			this.useGpu = useGpu;
			return this;
		}

		public Builder minSize( final double minSize )
		{
			this.minSize = minSize;
			return this;
		}

		public Cellpose3Parameters build()
		{
			return new Cellpose3Parameters(
					model, customModel, diameter, do3D, channels, normalize,
					flowThreshold, cellProbThreshold, useGpu, minSize,
					anisotropy, stitchThreshold, resample, tileOverlap,
					computeFlows, flow3dSmooth, nIter );
		}

		public Builder anisotropy( final double anisotropy )
		{
			this.anisotropy = anisotropy;
			return this;
		}

		public Builder stitchThreshold( final double stitchThreshold )
		{
			this.stitchThreshold = stitchThreshold;
			return this;
		}

		public Builder resample( final boolean resample )
		{
			this.resample = resample;
			return this;
		}

		public Builder computeFlows( final boolean computeFlows )
		{
			this.computeFlows = computeFlows;
			return this;
		}

		public Builder flow3dSmooth( final int flow3dSmooth )
		{
			this.flow3dSmooth = flow3dSmooth;
			return this;
		}

		public Builder nIter( final int nIter )
		{
			this.nIter = nIter;
			return this;
		}
	}

	// Default parameters for common use cases
	public static Cellpose3Parameters defaultCytoParameters()
	{
		return builder()
				.model( Cellpose3Model.CYTO3 )
				.channels( 0, 0 )
				.diameter( 30.0 )
				.build();
	}

	public static Cellpose3Parameters defaultNucleiParameters()
	{
		return builder()
				.model( Cellpose3Model.NUCLEI )
				.channels( 0, 0 )
				.diameter( 17.0 )
				.build();
	}
}