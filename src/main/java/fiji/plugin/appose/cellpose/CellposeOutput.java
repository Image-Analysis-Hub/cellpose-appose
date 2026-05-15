package fiji.plugin.appose.cellpose;

import net.imagej.ImgPlus;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;

/**
 * Represents the output of Cellpose. Stores masks and flows possibly.
 */
public class CellposeOutput< T extends IntegerType< T > >
{

	/**
	 * The labels output from Cellpose. Can be {@link UnsignedIntType} or
	 * {@link UnsignedLongType}.
	 */
	public final ImgPlus< T > labels;

	/**
	 * The flows output from Cellpose. Always 3 channels.
	 */
	public final ImgPlus< UnsignedByteType > flows;

	
	public CellposeOutput( final ImgPlus< T > labels )
	{
		this( labels, null );
	}

	public CellposeOutput( final ImgPlus< T > labels, final ImgPlus< UnsignedByteType > flows )
	{
		this.labels = labels;
		this.flows = flows;
	}
}
