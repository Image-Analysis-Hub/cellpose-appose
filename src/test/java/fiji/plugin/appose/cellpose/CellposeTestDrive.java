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

import java.awt.Color;
import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import fiji.plugin.appose.ApposeUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import net.imglib2.cellpose.ApposeTaskListener;
import net.imglib2.cellpose.Cellpose3BuiltinModels;
import net.imglib2.cellpose.Cellpose3Parameters;
import net.imglib2.cellpose.Cellpose4Parameters;

/**
 * Interactive tests drive for Cellpose 3, using the static methods.
 */
public class CellposeTestDrive
{

	public static void main( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		try
		{
			ImageJ.main( args );
			final ApposeTaskListener listener = new FijiApposeTaskListener( "Cellpose 3" );
			final ImagePlus imp = IJ.openImage( "/Users/tinevez/Development/TrackMateWS/CellMigration-4.tif" );
//			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			//final ImagePlus imp = IJ.openImage( "/Users/tinevez/Desktop/R2_multiC-crop-1.tif" );
			imp.show();
			
			// create the ROI to test
			 final Roi circleROI = new OvalRoi(0, 0, 120, 70); // x, y, width, height
		     circleROI.setLocation(80, 60); 
		     circleROI.setImage( imp );
			imp.setRoi( circleROI );
		     final Roi copy = ( Roi ) imp.getRoi().clone();

			/*
			 * Cellpose 3
			 */

//			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.defaultCytoParameters();
			final Cellpose3Parameters paramsCP3 = Cellpose3Parameters.builder()
					.model( Cellpose3BuiltinModels.CYTO2 )
					.diameter( 30 )
					.build();
			final ImagePlus[] outputCP3 = Cellpose.cellpose3( imp, paramsCP3, listener );
			final ImagePlus labelsCP3 = outputCP3[ 0 ];

			IJ.selectWindow( imp.getID() );
			ApposeUtils.addROIs( labelsCP3, "Cellpose-3", Color.BLUE );
			RoiManager.getInstance2().runCommand( "Show All" );
			labelsCP3.show();
			
			/*
			 * Cellpose 4
			 */

			final Cellpose4Parameters paramsCP4 = Cellpose4Parameters.defaultParameters();
			imp.setRoi( copy ); // put it back.
			final ImagePlus[] outputCP4 = Cellpose.cellpose4( imp, paramsCP4, listener );
			final ImagePlus labelsCP4 = outputCP4[ 0 ];

			IJ.selectWindow( imp.getID() );
			ApposeUtils.addROIs( labelsCP4, "Cellpose-SAM", Color.RED );
			RoiManager.getInstance2().runCommand( "Show All" );
			labelsCP4.show();
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
