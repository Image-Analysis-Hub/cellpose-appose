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
package fiji.plugin.appose.cellpose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import fiji.plugin.appose.ApposeUtils;
import fiji.plugin.appose.ImageAxisInfo;
import ij.ImagePlus;
import ij.gui.NewImage;

public class TestApposeUtils 
{

	@Test
	void test_convertChannelChoice() 
	{
		// CP3 and CP4 when None is selected -> Null
		assertNull( ApposeUtils.convertChannelChoiceToInt( "None", true ), "None channel selection should be converted to null in CP3" );
		assertNull( ApposeUtils.convertChannelChoiceToInt( "None", false ), "None channel selection should be converted to null in CP4" );
		//assertEquals( ApposeUtils.convertChannelChoiceToInt( "None", true ), -1, "Converting channel choice to cellpose-compatible channel, wrong when select None for cp3" );
		//assertEquals( ApposeUtils.convertChannelChoiceToInt( "None", false ), -1, "Converting channel choice to cellpose-compatible channel, wrong when select None for cp4" );
		// CP3 and CP4 when channel 1 is selected -> 1 for CP3, 0 for CP4
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "1", true ), 1 );
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "1", false ), 0 );
		// CP3 has the option Average -> return 0
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "Average", true ), 0 );				
	}
	
	@Test
	void test_getImageDimensions()
	{
		// 4D image with C, Z, X and Y 
		final int nC = 8;
		final int nZ = 4;
		final int nT = 1;
		final int width = 300;
		final int height = 100;
		final int nSlices = nC * nZ * nT;
		final ImagePlus imp = NewImage.createByteImage( "TestImage", width, height, nSlices, NewImage.FILL_RAMP );
		imp.setDimensions( nC, nZ, nT );
		ImageAxisInfo infos = ApposeUtils.getImageAxisInfo( imp );
		// C, Z, X and Y -> to python, C should be 1 and Z 0
		assertEquals( infos.channel_axis, 1 );
		assertEquals( infos.z_axis, 0 );
		assertNull( infos.time_axis );
		// T, Z, X and Y -> to python, C should be null, Z->1, T->0
		imp.setDimensions( 1, 8, 4 );
		infos = ApposeUtils.getImageAxisInfo( imp );
		assertEquals( infos.z_axis, 1 );
		assertEquals( infos.time_axis, 0 );
		assertNull( infos.channel_axis );
		// Z, X and Y -> to python, C should be null, Z->0, T->null
		imp.setDimensions( 1, 8*4, 1 );
		infos = ApposeUtils.getImageAxisInfo( imp );
		assertEquals( infos.z_axis, 0 );
		assertNull( infos.time_axis );
		assertNull( infos.channel_axis );
		// C, X and Y -> to python, C ->0, Z->null, T->null
		imp.setDimensions( 8*4, 1, 1 );
		infos = ApposeUtils.getImageAxisInfo( imp );
		assertEquals( infos.channel_axis, 0 );
		assertNull( infos.time_axis );
		assertNull( infos.z_axis );
	}

}
