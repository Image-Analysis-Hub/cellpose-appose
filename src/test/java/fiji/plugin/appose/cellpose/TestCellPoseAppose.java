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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.Initializable;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.module.Module;

import fiji.plugin.appose.cellpose.cp3.Cellpose3Parameters;
import fiji.plugin.appose.cellpose.cp3.CellposeAppose;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.plugin.Duplicator;
import ij.process.ImageStatistics;



public class TestCellPoseAppose 
{
	
	/** Check that the pixi file is found and read 
	 * throws ModuleException */
	@Test
	public void readPixi() throws Exception
	{
		try
		{
			final String pixi_content = CellposeRunner.pixiEnv();
			assertNotEquals( "", pixi_content, "Pixi content read is empty" );
			assertTrue( pixi_content.contains("cellpose"), "Something is wrong in pixi content read: "+pixi_content );
			assertTrue( pixi_content.contains("dependencies"), "Something is wrong in pixi content read: "+pixi_content );
		} 
		catch (final Exception e) 
		{
			e.printStackTrace();
			throw e;
		}
	}
	
	/** Can now run it with the static version*/
	public void testDefaultRun() throws Exception 
	{
		try
		{
			final int nSlices = 20;		
			final ImagePlus imp = NewImage.createByteImage( "TestImage", 100, 50, nSlices, NewImage.FILL_RAMP );
			imp.setDimensions( 1, nSlices, 1 );
			WindowManager.setTempCurrentImage( imp ); 
	
			final Context ctx = new Context();
			final CommandInfo info = ctx.service(CommandService.class).getCommand(CellposeAppose.class);
			final Module module = info.createModule();
		    
			// Inject services into the module's command instance
			ctx.inject(module.getDelegateObject());
		
			// Manually call initialize()
			if (module.getDelegateObject() instanceof Initializable) 
			{
				((Initializable) module.getDelegateObject()).initialize();
			}
			// Test default parameters value
			assertEquals( "cyto3", module.getInput("cp_model") );
			assertEquals( 30, module.getInput("cell_diameter") );
			assertEquals( "None", module.getInput("mode_3d") );
	
			// Test running cellpose, installing env if necessary
			final CellposeAppose cp3 = (CellposeAppose) module.getDelegateObject();
			cp3.setInput( "cyto_channel", 1);
			cp3.run();
			ctx.dispose();
		}
		finally {
	        WindowManager.setTempCurrentImage(null);  // clean up
	    }
	}
	
	@Test
	public void defaultRunCP3() throws Exception
	{
		try 
		{
			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			// Get all default parameters
			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.builder()
			.build();
			// Run it
			final ImagePlus[] outputCP3 = Cellpose.cellpose3( imp, paramsCP3 );
			// Get the label image results
			final ImagePlus labelsCP3 = outputCP3[ 0 ];
			// Check the image statistics if it looks like a successfull run
			final ImageStatistics stats = labelsCP3.getStatistics();
			assertFalse( stats.max <= 0, "CP3: No labels were found in blob image, with default parameters" );
			assertTrue( stats.max > 50, "CP3: Not enough labels were found in blob image, with default parameters" );
		}
		catch (final Exception e)
		{
			throw e;
		}
	}
	
	@Test
	public void runCP3ModelNuclei() throws Exception
	{
		try 
		{
			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			// Get all default parameters
			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.builder()
					.model( Cellpose3BuiltinModels.NUCLEI )
			.build();
			// Run it
			final ImagePlus[] outputCP3 = Cellpose.cellpose3( imp, paramsCP3 );
			// Get the label image results
			final ImagePlus labelsCP3 = outputCP3[ 0 ];
			// Check the image statistics if it looks like a successfull run
			final ImageStatistics stats = labelsCP3.getStatistics();
			assertFalse( stats.max <= 0, "CP3: No labels were found in blob image, with nuclei model" );
			assertTrue( stats.max > 50, "CP3: Not enough labels were found in blob image, with nuclei model" );
		}
		catch (final Exception e)
		{
			throw e;
		}
	}
	
	@Test
	public void runCP3_Image3DMultiChannels() throws Exception
	{
		try 
		{
			final ImagePlus stack = IJ.openImage( "https://imagej.net/images/mitosis.tif" );
			// Keep only one time point
			final ImagePlus imp = new Duplicator().run(
						stack,
						1, stack.getNChannels(),   
						1, stack.getNSlices(),      
						3, 3     
			);
			// Get all default parameters
			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.builder()
					.do3D(false)
					.stitchThreshold(0.1)
					.channels(1, null)
			.build();
			// Run it
			final ImagePlus[] outputCP3 = Cellpose.cellpose3( imp, paramsCP3 );
			// Get the label image results
			final ImagePlus labelsCP3 = outputCP3[ 0 ];
			// Check the label image dimensions
			assertEquals( imp.getWidth(), labelsCP3.getWidth(), "CP3, 3D+chan image: labels image dimension (width) is uncorrect" );
			assertEquals( imp.getHeight(), labelsCP3.getHeight(), "CP3, 3D+chan image: labels image dimension (height) is uncorrect" );
			
			// Check the image statistics if it looks like a successfull run
			labelsCP3.setSlice(2);
			final ImageStatistics stats = labelsCP3.getStatistics();
			assertFalse( stats.max <= 0, "CP3: No labels were found in test image, with default parameters" );
			assertTrue( stats.max > 1, "CP3: Not enough labels were found in test image, with default parameters" );
		}
		catch (final Exception e)
		{
			throw e;
		}
	}
	
	@Test
	public void runCP3_ImageTimeMultiChannels() throws Exception
	{
		try 
		{
			final ImagePlus stack = IJ.openImage( "https://imagej.net/images/mitosis.tif" );
			// Keep only one slice
			final ImagePlus imp = new Duplicator().run(
						stack,
						1, stack.getNChannels(),   
						3, 3,      
						1, 6     
			);
			// Get all default parameters
			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.builder()
					.channels(1, null)
			.build();
			// Run it
			final ImagePlus[] outputCP3 = Cellpose.cellpose3( imp, paramsCP3 );
			// Get the label image results
			final ImagePlus labelsCP3 = outputCP3[ 0 ];
			// Check the label image dimensions
			assertEquals( imp.getWidth(), labelsCP3.getWidth(), "CP3, time+chan image: labels image dimension (width) is uncorrect" );
			assertEquals( imp.getHeight(), labelsCP3.getHeight(), "CP3, time+chan image: labels image dimension (height) is uncorrect" );
			
			// Check the image statistics if it looks like a successfull run
			labelsCP3.setSlice(2);
			final ImageStatistics stats = labelsCP3.getStatistics();
			assertFalse( stats.max <= 0, "CP3: No labels were found in test image, with default parameters" );
			assertTrue( stats.max > 2, "CP3: Not enough labels were found in test image, with default parameters" );
		}
		catch (final Exception e)
		{
			throw e;
		}
	}
}
