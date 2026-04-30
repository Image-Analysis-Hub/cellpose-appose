/*-
 * #%L
 * Running Cellpose with a Fiji plugin based on Appose.
 * %%
 * Copyright (C) 2026 Appose developpers
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the My Company nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

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
