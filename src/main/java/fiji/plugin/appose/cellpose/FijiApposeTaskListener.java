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

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.apposed.appose.Builder.ProgressConsumer;
import org.apposed.appose.TaskEvent;

import ij.IJ;
import net.imglib2.cellpose.ApposeTaskListener;

public class FijiApposeTaskListener implements ApposeTaskListener
{

	private volatile JDialog progressDialog;

	private volatile JProgressBar progressBar;

	private volatile ScheduledFuture< ? > delayedShowTask;

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool( 1 );

	/*
	 * Normal Appose messages -> IJ toolbar.
	 */

	@Override
	public Consumer< TaskEvent > taskListener()
	{
		return e -> {
			closeProgressDialog();
			//System.out.println(e.message);
			if ( e.message != null && !e.message.trim().isEmpty() )
				IJ.showStatus( e.responseType + ": " + e.message );
			if ( e.current >= 0 && e.maximum > 0 )
				IJ.showProgress( ( int ) e.current, ( int ) e.maximum );
		};
	}
	

	@Override
	public void message( final String msg )
	{
		IJ.showStatus( msg );	
	}

	/*
	 * Installation messages -> Custom progres dialog.
	 */

	@Override
	public Consumer< String > outputListener()
	{
		return str -> log( str );
	}

	@Override
	public Consumer< String > errorListener()
	{
		return str -> {
			/*
			 * We have an issue here: pixi always return an error message that
			 * says "✔ The cp4-cpu environment has been installed." when the
			 * environment is ready, even if it was already installed. So we
			 * need to filter out this message to avoid showing an error dialog.
			 */
			if ( str != null && str.contains( "The" ) && str.contains( "environment has been installed." ) )
			{
				final String envName = str.substring( str.indexOf( "The" ) + 3, str.indexOf( "environment" ) );
				IJ.showStatus( "Python environment " + envName + " is ready." );
			}
			else
			{
				// Actual error.
				log( "ERROR: " + str );
			}
		};
	}

	@Override
	public ProgressConsumer progressListener()
	{
		return ( msg, cur, max ) -> log( msg, cur, max );
	}
	
	/** Close progress dialog bar if it exists */
	public void closeProgressDialog()
	{
		if ( progressDialog != null )
			progressDialog.dispose();
		progressDialog = null;
	}

	public void close()
	{
		EventQueue.invokeLater( () -> {
			// Cancel the delayed show if it hasn't run yet
			if ( delayedShowTask != null )
			{
				delayedShowTask.cancel( false );
				delayedShowTask = null;
			}

			if ( progressDialog != null )
				progressDialog.dispose();
			progressDialog = null;
		} );
	}

	private void log( final String msg, final Long cur, final Long max )
	{
		System.out.println( "Received msg: " + msg + " cur: " + cur + " max: " + max );
		EventQueue.invokeLater( () -> {
			if ( progressDialog == null )
			{
				// Schedule the dialog to appear after 1 second
				if ( delayedShowTask == null )
				{
					delayedShowTask = scheduler.schedule( () -> {
						EventQueue.invokeLater( () -> {
							if ( progressDialog == null  )
								createAndShowDialog();
						} );
					}, 1, TimeUnit.SECONDS );
				}
				return; // Don't update yet, dialog not visible
			}

			// Update existing dialog
			updateProgressBar( msg, cur, max );
		} );
	}

	private void log( final String msg )
	{
		log( msg, null, null );
	}

	private void createAndShowDialog()
	{
		final Window owner = IJ.getInstance();
		progressDialog = new JDialog( owner, "Fiji ♥ Appose" );
		progressDialog.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		progressBar = new JProgressBar();
		progressDialog.getContentPane().add( progressBar );
		progressBar.setFont( new Font( "Courier", Font.PLAIN, 14 ) );
		progressBar.setString(
				"--------------------==================== " +
						"Building Python environment " +
						"====================--------------------" );
		progressBar.setStringPainted( true );
		progressBar.setIndeterminate( true );
		progressDialog.pack();
		progressDialog.setLocationRelativeTo( owner );
		progressDialog.setVisible( true );
		delayedShowTask = null;
	}

	private void updateProgressBar( final String msg, final Long cur, final Long max )
	{
		if ( msg != null && !msg.trim().isEmpty() )
			progressBar.setString( "Building Python environment: " + msg.trim() );
		if ( cur != null || max != null )
			progressBar.setIndeterminate( false );
		if ( max != null )
			progressBar.setMaximum( max.intValue() );
		if ( cur != null )
			progressBar.setValue( cur.intValue() );
	}
}
