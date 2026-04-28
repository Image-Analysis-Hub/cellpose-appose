package fiji.plugin.appose.cellpose.cp3;

import java.util.List;

/**
 * Data class to hold parameters for Cellpose segmentation.
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
		double stitchThreshold )
{
	// Default constructor with validation
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
					anisotropy, stitchThreshold );
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