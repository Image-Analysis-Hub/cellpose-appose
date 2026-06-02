package fiji.plugin.appose.cellpose;
import java.awt.Desktop;
import java.net.URI;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins>Segmentation>Cellpose-Appose>Help..." )
public class OpenDocumentation implements Command
{
	@Override
	public void run()
	{
	        try {
	            // Check if Desktop is supported
	            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
	                // Open the default browser with the URL
	                Desktop.getDesktop().browse(new URI("https://imagej.net/plugins/cellpose-appose"));
	            } else {
	                System.out.println("Desktop browsing not supported");
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	}
}
