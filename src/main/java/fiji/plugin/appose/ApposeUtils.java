package fiji.plugin.appose;

import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.numeric.real.DoubleType;

public class ApposeUtils
{

	/**
	 * A utility to wrap an ImagePlus into an ImgPlus, without too many
	 * warnings. Hacky.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static final < T > ImgPlus< T > rawWraps( final ImagePlus imp )
	{
		final ImgPlus< DoubleType > img = ImagePlusAdapter.wrapImgPlus( imp );
		final ImgPlus raw = img;
		return raw;
	}
}
