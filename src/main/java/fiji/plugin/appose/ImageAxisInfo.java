/*-
 * #%L
 * Running Cellpose with a Fiji plugin based on Appose.
 * %%
 * Copyright (C) 2026 My Company, Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
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
