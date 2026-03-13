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

import static fiji.plugin.appose.ApposeUtils.rawWraps;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Interactive tests to see what dimensionality Python sees after receiving an
 * image from Java.
 */
public class DimensionalityTest
{

	private < T extends RealType< T > & NativeType< T > > void sendToPython( final ImagePlus imp )
	{
		// Build a dictionary of dim size -> dim id
		final String impDimOrder = "XYCZT";
		final int[] dims = imp.getDimensions();
		final Map< Integer, Character > map = new HashMap<>();
		for ( int i = 0; i < dims.length; i++ )
		{
			if ( dims[ i ] > 1 )
				map.put( dims[ i ], impDimOrder.charAt( i ) );
		}
		System.out.println( "Dimension mapping: " + map );

		final ImgPlus< T > img = rawWraps( imp );
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );
		inputs.put( "dims", map );

		try
		{
			final Environment env = Appose // the builder
					.pixi()
					.content( pixiEnv() )
					.subscribeError( IJ::log )
					.build();

			/*
			 * Using this environment, we create a service that will run the
			 * Python script.
			 */
			try (Service python = env.python())
			{
				/*
				 * With this service, we can now create a task that will run the
				 * Python script with the specified inputs. This command takes
				 * the script as first argument, and a map of inputs as second
				 * argument. The keys of the map will be the variable names in
				 * the Python script, and the values are the data that will be
				 * passed to Python.
				 */
				final Task task = python.task( script(), inputs );

				// Start the script, and return to Java immediately.
				System.out.println( "Starting task" );
				final long start = System.currentTimeMillis();
				task.start();

				/*
				 * Wait for the script to finish. This will block the Java
				 * thread until the Python script is done, but it allows the
				 * Python code to run in parallel without blocking the Java
				 * thread while it is running.
				 */
				task.waitFor();

				// Verify that it worked.
				if ( task.status != TaskStatus.COMPLETE )
					throw new RuntimeException( "Python script failed with error: " + task.error );

				// Benchmark.
				final long end = System.currentTimeMillis();
				System.out.println( "Task finished in " + ( end - start ) / 1000. + " s" );

				System.out.println();
				final Object obj1 = task.outputs.get( "imDim" );
				System.out.println( "Python script output 1: " + obj1 + " <- " + obj1.getClass() );
				final Object obj2 = task.outputs.get( "dims" );
				System.out.println( "Python script output 2: " + obj2 );

				final List< Character > dimOrder = new ArrayList<>();
				for ( final Object o : ( List< ? > ) obj1 )
					dimOrder.add( map.get( o ) );
				
				System.out.println( "Dimension order seen in Python: " + dimOrder );
				
			}
			catch ( final Exception e )
			{
				System.err.println( "Error running Python script: " + e.getMessage() );
			}
		}
		catch ( final BuildException e )
		{
			System.err.println( "Error building Appose environment: " + e.getMessage() );
		}
	}

	private String script()
	{
		String script = "";
		try
		{
			final URL scriptFile = this.getClass().getResource( "/test_dims.py" );
			script = IOUtils.toString( scriptFile, StandardCharsets.UTF_8 );

		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return script;
	}

	private String pixiEnv()
	{
		String env = "";
		try
		{
			final URL pixiFile = this.getClass().getResource( "/pixi.toml" );
			env = IOUtils.toString( pixiFile, StandardCharsets.UTF_8 );

		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return env;
	}

	public static void main( final String[] args )
	{
		final int nC = 8;
		final int nZ = 5;
		final int nT = 1;
		final int width = 600;
		final int height = 300;
		final int nSlices = nC * nZ * nT;
		final ImagePlus imp = NewImage.createByteImage( "TestImage", width, height, nSlices, NewImage.FILL_RAMP );
		imp.setDimensions( nC, nZ, nT );
		new DimensionalityTest().sendToPython( imp );
	}
}
