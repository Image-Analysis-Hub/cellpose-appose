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

import org.junit.jupiter.api.Test;
import fiji.plugin.appose.cellpose.cp4.Cellpose4Parameters;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.ChannelSplitter;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.process.ImageStatistics;

import static org.junit.jupiter.api.Assertions.*;



public class TestCellPoseSAMAppose 
{
	
	@Test
	public void defaultRunCPSAM() throws Exception
	{
		try 
		{
			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			// Get all default parameters
			final Cellpose4Parameters paramsCP4 = Cellpose4Parameters.builder()
			.build();
			// Run it
			final ImagePlus[] outputCP4 = Cellpose.cellpose4( imp, paramsCP4 );
			// Get the label image results
			final ImagePlus labelsCP4 = outputCP4[ 0 ];
			// Check the label image dimensions
			assertEquals( labelsCP4.getWidth(), imp.getWidth(), "CP4, 2D image: labels image dimension (width) is uncorrect" );
			assertEquals( labelsCP4.getHeight(), imp.getHeight(), "CP4, 2D image: labels image dimension (height) is uncorrect" );
			
			// Check the label image statistics if it looks like a successfull run
			ImageStatistics stats = labelsCP4.getStatistics();
			assertFalse( stats.max <= 0, "CP4: No labels were found in blob image, with default parameters" );
			assertTrue( stats.max > 50, "CP4: Not enough labels were found in blob image, with default parameters" );
		}
		catch (Exception e)
		{
			throw e;
		}
	}
	
	// Test different input image dimensions
	@Test
	public void defaultRunCPSAM_Image2DMultiChannels() throws Exception
	{
		
		try 
		{
			// 2D image with 3 channels
			final ImagePlus impRGB = IJ.openImage( "https://imagej.net/images/FluorescentCells.jpg" );
			// convert RGB to composite ImagePlus
			ImagePlus[] channels = ChannelSplitter.split( impRGB );
		    final ImagePlus imp = RGBStackMerge.mergeChannels(channels, false);
			// Get all default parameters
			final Cellpose4Parameters paramsCP4 = Cellpose4Parameters.builder()
			.build();
			// Run it
			final ImagePlus[] outputCP4 = Cellpose.cellpose4( imp, paramsCP4 );
			// Get the label image results
			final ImagePlus labelsCP4 = outputCP4[ 0 ];
			// Check the label image dimensions
			assertEquals( imp.getWidth(), labelsCP4.getWidth(), "CP4, 2D image: labels image dimension (width) is uncorrect" );
			assertEquals( imp.getHeight(), labelsCP4.getHeight(), "CP4, 2D image: labels image dimension (height) is uncorrect" );
			
			// Check the label image statistics if it looks like a successfull run
			ImageStatistics stats = labelsCP4.getStatistics();
			assertFalse( stats.max <= 0, "CP4: No labels were found in blob image, with default parameters" );
			assertTrue( stats.max > 3, "CP4: Not enough labels were found in blob image, with default parameters" );
		}
		catch (Exception e)
		{
			throw e;
		}
	}
	
	@Test
	public void defaultRunCPSAM_Image3DMultiChannels() throws Exception
	{	
		// New image
		try 
		{
			// 4D image with channels, Z, time
			final ImagePlus stack = IJ.openImage( "https://imagej.net/images/mitosis.tif" );
			// Keep only one time point
			ImagePlus imp = new Duplicator().run(
				    stack,
				    1, stack.getNChannels(),   
				    1, stack.getNSlices(),      
				    3, 3     
				);
			// Get all default parameters
			final Cellpose4Parameters paramsCP4 = Cellpose4Parameters.builder()
					.chan0(0)
					.chan1(null)
					.do3D( false )
					.stitchThreshold( 0.1 )
			.build();
			// Run it
			final ImagePlus[] outputCP4 = Cellpose.cellpose4( imp, paramsCP4 );
			// Get the label image results
			final ImagePlus labelsCP4 = outputCP4[ 0 ];
			// Check the label image dimensions
			assertEquals( imp.getWidth(), labelsCP4.getWidth(), "CP4, 3D+chan image: labels image dimension (width) is uncorrect" );
			assertEquals( imp.getHeight(), labelsCP4.getHeight(), "CP4, 3D+chanD image: labels image dimension (height) is uncorrect" );
			
			// Check the label image statistics if it looks like a successfull run
			ImageStatistics stats = labelsCP4.getStatistics();
			assertFalse( stats.max <= 0, "CP4: No labels were found in 4D image, with default parameters" );
			assertTrue( stats.max == 1, "CP4: Too much labels were found in 4D image, with default parameters" );
		}
		catch (Exception e)
		{
			throw e;
		}
	}
	
}
