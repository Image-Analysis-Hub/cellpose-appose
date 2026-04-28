package fiji.plugin.appose.cellpose;

import java.util.List;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import ij.IJ;

@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>Cellpose-Appose>Set advanced options...")
public class AdvancedOptions implements Command
{
	@Parameter
    private PrefService prefService; 
	
	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String info_version = "-------- Change torch/cuda versions (Windows and Linux)";
	
	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String info_type = "Change it to a precise version number: eg \"12.6\"";
	
	
	@Parameter( label="torch_version", description="Choose the version of pytorch to install in the env" )
	private String torch_version = "Default";
	
	@Parameter( label="torchvision_version", description="Choose the version of torchvision to install in the env" )
	private String torchvision_version = "Default";
	
	@Parameter( label="cuda_version", description="Choose the version of cuda to install in the env" )
	private String cuda_version = "Default";
	
	/**
	 * Check that the values entered in the module versions are Default or numbers
	 */
	public void checkValidity()
	{
		checkModule( "torch_version", torch_version );
		checkModule( "torchvision_version", torchvision_version );
		checkModule( "cuda_version", cuda_version );
	}

		/**
		 * Check the version entered of a module and modify it if needed
		 * param module
		 */
	public void checkModule( String module, String value )
	{
		boolean ok = moduleVersionValidity( value );
		if ( !ok )
		{
			IJ.log( "Version for module "+module+" is not valid: should be Default or a number of version." );
			IJ.log( "The version is set back to Default. Re-update with a valid version if you want to modify it." );
			prefService.put(AdvancedOptions.class, module, "Default");
		}
	}
	
	/** 
	 * Check that the string is Default or a number with points
	 * param version
	 * return
	 */
	public boolean moduleVersionValidity( String version )
	{
		if ( version.equals("Default") )
			return true;
		
		return version.matches("[0-9.]+");
	}
	
	/**
	 * Check and change if necessary some modules version
	 * param env
	 * return
	 */
	public static String handleModuleVersion( PrefService prefService, String env )
	{
		String tversion = prefService.get(AdvancedOptions.class, "torch_version", "Default");
			if ( !tversion.equals("Default") )
			{
				env = env.replace( "torch = { version = \">=2.5.1\", index = \"https://download.pytorch.org/whl/cu126\" }",
		        "torch = { version = \"=="+tversion+"\", index = \"https://download.pytorch.org/whl/cu126\" }");
			}
			String tvversion = prefService.get(AdvancedOptions.class, "torchvision_version", "Default");
			if ( !tvversion.equals("Default") )
			{
				env = env.replace( "torchvision = { version = \">=0.20.1\", index = \"https://download.pytorch.org/whl/cu126\" }",
		        "torchvision = { version = \"=="+tvversion+"\", index = \"https://download.pytorch.org/whl/cu126\" }");
			}
			String cuversion = prefService.get(AdvancedOptions.class, "cuda_version", "Default");
			if ( !cuversion.equals("Default") )
			{
				env = env.replace( "index = \"https://download.pytorch.org/whl/cu126\" }",
		        "index = \"https://download.pytorch.org/whl/cu"+cuversion.replace(".", "")+"\" }");
			}	
		return env;
	}
	
	@Override
	public void run()
	{
		// Check validity of the versions entered
		checkValidity();
		IJ.log("Advanced options setted");
	}
}
