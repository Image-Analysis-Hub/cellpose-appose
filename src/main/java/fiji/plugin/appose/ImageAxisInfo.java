
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
}
