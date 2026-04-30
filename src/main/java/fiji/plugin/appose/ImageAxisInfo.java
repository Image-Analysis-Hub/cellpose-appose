
package fiji.plugin.appose;

import net.imagej.ImgPlus;

public class ImageAxisInfo
{
	public final Integer z_axis;

	public final Integer channel_axis;

	public final Integer time_axis;

	public ImageAxisInfo( final Integer z_axis, final Integer channel_axis, final Integer time_axis )
	{
		this.z_axis = z_axis;
		this.channel_axis = channel_axis;
		this.time_axis = time_axis;
	}

	public static ImageAxisInfo fromImgPlus( final ImgPlus< ? > img )
	{
		Integer zAxis = null;
		Integer channelAxis = null;
		Integer timeAxis = null;

		for ( int i = 0; i < img.numDimensions(); i++ )
		{
			final String axisType = img.axis( i ).type().toString().trim();
			switch ( axisType )
			{
			case "Z":
				zAxis = i;
				break;
			case "Channel":
				channelAxis = i;
				break;
			case "Time":
				timeAxis = i;
				break;
			default:
				// Ignore other axes
			}
		}

		return new ImageAxisInfo( zAxis, channelAxis, timeAxis );
	}
	
	public static ImageAxisInfo fromImgPlusToPython( final ImgPlus< ? > img )
	{
		int nzAxis = 1;
		int nchannelAxis = 1;
		int ntimeAxis = 1;

		for ( int i = 0; i < img.numDimensions(); i++ )
		{
			final String axisType = img.axis( i ).type().toString().trim();
			switch ( axisType )
			{
			case "Z":
				nzAxis = (int) img.dimension( i );
				break;
			case "Channel":
				nchannelAxis = (int) img.dimension( i );
				break;
			case "Time":
				ntimeAxis = (int) img.dimension( i );
				break;
			default:
				// Ignore other axes
			}
		}
		return ApposeUtils.convertImageAxis( nzAxis, nchannelAxis, ntimeAxis );
	}
}
