package fiji.plugin.appose.cellpose;

import org.junit.jupiter.api.Test;
import org.python.antlr.PythonParser.raise_stmt_return;
import org.scijava.Context;
import org.scijava.Initializable;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.module.Module;
import org.scijava.module.ModuleException;

import fiji.plugin.appose.cellpose.cp3.CellposeAppose;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.NewImage;

import static org.junit.jupiter.api.Assertions.*;



public class TestCellPoseAppose 
{
	
	/** Check that the pixi file is found and read 
	 * throws ModuleException */
	@Test
	public void readPixi() throws ModuleException
	{
		Context ctx = new Context();
		CommandInfo info = ctx.service(CommandService.class).getCommand(CellposeAppose.class);
		Module module;
		try {
			module = info.createModule();
		
	    
		// Inject services into the module's command instance
		ctx.inject(module.getDelegateObject());
		CellposeAppose cp3 = (CellposeAppose) module.getDelegateObject();
		String pixi_content = cp3.pixiEnv();
		assertNotEquals( "", pixi_content, "Pixi content read is empty" );
		assertTrue( pixi_content.contains("cellpose"), "Something is wrong in pixi content read: "+pixi_content );
		assertTrue( pixi_content.contains("dependencies"), "Something is wrong in pixi content read: "+pixi_content );
		} 
		catch (ModuleException e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	@Test
	public void testDefaultParameterValues() throws Exception 
	{
		try
		{
			final int nSlices = 20;		
			final ImagePlus imp = NewImage.createByteImage( "TestImage", 100, 50, nSlices, NewImage.FILL_RAMP );
			imp.setDimensions( 1, nSlices, 1 );
			WindowManager.setTempCurrentImage( imp ); 
	
			Context ctx = new Context();
			CommandInfo info = ctx.service(CommandService.class).getCommand(CellposeAppose.class);
			Module module = info.createModule();
		    
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

			ctx.dispose();
		}
		finally {
	        WindowManager.setTempCurrentImage(null);  // clean up
	    }
	}
	
	@Test
	public void testDefaultRun() throws Exception 
	{
		try
		{
			final int nSlices = 20;		
			final ImagePlus imp = NewImage.createByteImage( "TestImage", 100, 50, nSlices, NewImage.FILL_RAMP );
			imp.setDimensions( 1, nSlices, 1 );
			WindowManager.setTempCurrentImage( imp ); 
	
			Context ctx = new Context();
			CommandInfo info = ctx.service(CommandService.class).getCommand(CellposeAppose.class);
			Module module = info.createModule();
		    
			// Inject services into the module's command instance
			ctx.inject(module.getDelegateObject());
		
			// Manually call initialize()
			if (module.getDelegateObject() instanceof Initializable) 
			{
				((Initializable) module.getDelegateObject()).initialize();
			}
	
			CellposeAppose cp3 = (CellposeAppose) module.getDelegateObject();
			cp3.setInput( "cyto_channel", 1);
			cp3.run();
			ctx.dispose();
		}
		finally {
	        WindowManager.setTempCurrentImage(null);  // clean up
	    }
	}
}
