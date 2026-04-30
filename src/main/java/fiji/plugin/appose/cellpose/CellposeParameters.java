package fiji.plugin.appose.cellpose;

import java.util.HashMap;
import java.util.Map;

import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public abstract class CellposeParameters
{

	public final String customModel;

	// Core parameters

	public final double diameter;

	// Thresholds

	public final double flowThreshold;

	public final double cellProbThreshold;

	// Normalization & pre-processing

	public final boolean normalize;

	public final boolean resample;

	// 3D processing

	public final boolean do3D;

	public final double anisotropy;

	public final double stitchThreshold;

	public final int flow3dSmooth;

	// Advanced parameters

	public final boolean useGpu;

	public final double minSize;

	// Advanced processing

	public final double tileOverlap;

	public final boolean computeFlows;

	public final int nIter;

	protected CellposeParameters(
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
		this.customModel = customModel;
		this.diameter = diameter;
		this.do3D = do3D;
		this.normalize = normalize;
		this.flowThreshold = flowThreshold;
		this.cellProbThreshold = cellProbThreshold;
		this.useGpu = useGpu;
		this.minSize = minSize;
		this.anisotropy = anisotropy;
		this.stitchThreshold = stitchThreshold;
		this.resample = resample;
		this.tileOverlap = tileOverlap;
		this.computeFlows = computeFlows;
		this.flow3dSmooth = flow3dSmooth;
		this.nIter = nIter;
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
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "use_3D", do3D );
		// return null if custom model
		final boolean isBuiltInModel = customModel == null || customModel.equals( "" );
		inputs.put( "custom_model", isBuiltInModel ? null : customModel );
		inputs.put( "diameter", diameter );
		inputs.put( "stitch_threshold", stitchThreshold );
		inputs.put( "anisotropy", anisotropy );
		inputs.put( "compute_flows", computeFlows );
		inputs.put( "resample", resample );
		inputs.put( "normalize", normalize );
		inputs.put( "flow_threshold", flowThreshold );
		inputs.put( "cellprob_threshold", cellProbThreshold );
		inputs.put( "min_size", minSize );
		inputs.put( "tile_overlap", tileOverlap );
		inputs.put( "flow3D_smooth", flow3dSmooth );
		inputs.put( "niter", nIter <= 0 ? null : nIter );
		return inputs;
	}

	protected static abstract class Builder< B extends Builder< B > >
	{

		protected String customModel = null;

		// Core parameters

		protected double diameter = 30.0;

		// Thresholds

		protected double flowThreshold = 0.4;

		protected double cellProbThreshold = 0.0;

		// Normalization & pre-processing

		protected boolean normalize = true;

		protected boolean resample = false;

		// 3D processing

		protected boolean do3D = false;

		protected double anisotropy = 1.0;

		protected double stitchThreshold = 0.0;

		protected int flow3dSmooth = 0;

		// Advanced parameters

		protected boolean useGpu = true;

		protected double minSize = 15.0;

		// Advanced processing

		protected double tileOverlap = 0.1;

		protected boolean computeFlows = false;

		protected int nIter = 200;

		@SuppressWarnings( "unchecked" )
		public B customModel( final String customModel )
		{
			this.customModel = customModel;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B diameter( final double diameter )
		{
			this.diameter = diameter;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B do3D( final boolean do3D )
		{
			this.do3D = do3D;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B normalize( final boolean normalize )
		{
			this.normalize = normalize;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B flowThreshold( final double flowThreshold )
		{
			this.flowThreshold = flowThreshold;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B cellProbThreshold( final double cellProbThreshold )
		{
			this.cellProbThreshold = cellProbThreshold;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B useGpu( final boolean useGpu )
		{
			this.useGpu = useGpu;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B minSize( final double minSize )
		{
			this.minSize = minSize;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B anisotropy( final double anisotropy )
		{
			this.anisotropy = anisotropy;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B stitchThreshold( final double stitchThreshold )
		{
			this.stitchThreshold = stitchThreshold;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B resample( final boolean resample )
		{
			this.resample = resample;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B tileOverlap( final double tileOverlap )
		{
			this.tileOverlap = tileOverlap;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B computeFlows( final boolean computeFlows )
		{
			this.computeFlows = computeFlows;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B flow3dSmooth( final int flow3dSmooth )
		{
			this.flow3dSmooth = flow3dSmooth;
			return ( B ) this;
		}

		@SuppressWarnings( "unchecked" )
		public B nIter( final int nIter )
		{
			this.nIter = nIter;
			return ( B ) this;
		}

		public abstract CellposeParameters build();
	}
}
