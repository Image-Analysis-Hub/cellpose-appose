package fiji.plugin.appose;

public class ImageAxisInfo
{
	public final Integer z_axis;

	public final Integer channel_axis;

	public final Integer time_axis;

	public ImageAxisInfo( Integer z_axis, Integer channel_axis, Integer time_axis )
	{
		this.z_axis = z_axis;
		this.channel_axis = channel_axis;
		this.time_axis = time_axis;
	}
}
