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
package fiji.plugin.appose.cellpose;

import java.util.HashMap;
import java.util.Map;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import ij.IJ;

@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>Cellpose-Appose>Cellpose configuration...")
public class CellposeOptions implements Command
{
	@Parameter
    private PrefService prefService; 
	
	@Parameter(label="", visibility=ItemVisibility.MESSAGE)
    private final String messageTitle = "<html>" +
            "<table><tr valign='top'><td>" +
            "<h2>Advance configuration for Cellpose plugin</h2>" +
            "</tr></table>" +
            "</html>";
	
	@Parameter( label="<html><b>Torch backend</b></html>", choices = {"CUDA 12.6", "CUDA 13.0", "CPU"}, description="Choose the version of cuda to install in the env (only for Windows and Linux)." )
	private String backend_version = "CUDA 12.6";

	@Parameter( label="<html><i>Note:</i></html>", visibility=ItemVisibility.MESSAGE )
	private String noteMdg = "<i>- Use 'CPU' if you do not have a CUDA compatible device.</i>" +
			"<br/><i>- MacOs users automatically rely on the CPU version.</i>";
	
	private static final Map<String, String> versions_map = createCodeMap();

	private static Map<String, String> createCodeMap()
	{
		// Map to convert the user-friendly version names to the actual version codes used in the pixi environment specification
		// Extend this map along with the choices in the `cuda_version` parameter if you want to add more versions.
		Map<String, String> version_code_map = new HashMap<>();
		version_code_map.put("CUDA 12.6", "cu126");
		version_code_map.put("CUDA 13.0", "cu130");
		version_code_map.put("CPU", "cpu");
		return version_code_map;
	}

	/**
	 * Update Pixi environment specification according to the selected CUDA version.
	 */
	public static String handleTorchBackend( PrefService prefService, String env )
	{
		// Get the selected option and convert it to the corresponding version code
		String cversion = prefService.get(CellposeOptions.class, "backend_version");
		String code = versions_map.get(cversion);

		if (code != null) {
			System.err.println("Using " + cversion + " (" + code + ")");
			// Replace the index URL in the pixi environment specification according to the selected CUDA version. 
			// - Default pixi env configuration is for CUDA 12.6 for windows and linux
			// - If the user does not have CUDA device, then he should select "CPU" 
			env = env.replace( "\"https://download.pytorch.org/whl/cu126\"", "\"https://download.pytorch.org/whl/"+code+"\""); 
		}

		return env;
	}
	
	@Override
	public void run()
	{
		IJ.log("Advanced options setted");
	}
}
