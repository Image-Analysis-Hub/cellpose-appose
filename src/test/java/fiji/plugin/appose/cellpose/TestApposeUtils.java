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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import fiji.plugin.appose.ApposeUtils;

public class TestApposeUtils 
{

	@Test
	void test_convertChannelChoice() 
	{
		// CP3 and CP4 when None is selected -> Null
		assertNull( "None channel selection should be converted to null in CP3", ApposeUtils.convertChannelChoiceToInt( "None", true ) );
		assertNull( "None channel selection should be converted to null in CP4", ApposeUtils.convertChannelChoiceToInt( "None", false ) );
		//assertEquals( ApposeUtils.convertChannelChoiceToInt( "None", true ), -1, "Converting channel choice to cellpose-compatible channel, wrong when select None for cp3" );
		//assertEquals( ApposeUtils.convertChannelChoiceToInt( "None", false ), -1, "Converting channel choice to cellpose-compatible channel, wrong when select None for cp4" );
		// CP3 and CP4 when channel 1 is selected -> 1 for CP3, 0 for CP4
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "1", true ), ( Integer ) 1 );
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "1", false ), ( Integer ) 0 );
		// CP3 has the option Average -> return 0
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "Average", true ), ( Integer ) 0 );
	}
}
