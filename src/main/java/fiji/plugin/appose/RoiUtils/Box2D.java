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

package fiji.plugin.appose.RoiUtils;

/**
 * A bounding Box in 2 dimensions.
 *
 * see Box3D
 * 
 * @author dlegland
 *
 */
public class Box2D
{
	// ==================================================
	// Class variables
	
	double xmin;
	double xmax;
	double ymin;
	double ymax;
	
	// ==================================================
	// Constructors
	
	/**
	 * Default constructor of the bounding box, that specifies the bounds along
	 * each dimension.
	 * 
	 * @param xmin
	 *            the minimum x coordinate
	 * @param xmax
	 *            the maximum x coordinate
	 * @param ymin
	 *            the minimum y coordinate
	 * @param ymax
	 *            the maximum y coordinate
	 */
	public Box2D(final double xmin, final double xmax, final double ymin, final double ymax)
	{
		this.xmin = xmin;
		this.xmax = xmax;
		this.ymin = ymin;
		this.ymax = ymax;
	}
	
	// ==================================================
	// generic methods

	/**
	 * Computes the area of this bounding box.
	 * 
	 * @return the area enclosed by the bounding box.
	 */
	public double area()
	{
		return (xmax - xmin) * (ymax - ymin);
	}

	/**
	 * Computes the width of the bounding box, corresponding to the extent along
	 * the X dimension.
	 * 
	 * @return the width of the bounding box
	 */
	public double width()
	{
		return xmax - xmin;
	}
	
	/**
	 * Computes the height of the bounding box, corresponding to the extent
	 * along the Y dimension.
	 * 
	 * @return the height of the bounding box
	 */
	public double height()
	{
		return  ymax - ymin;
	}
	
	
	// ==================================================
	// accessors
	
	/**
	 * @return the minimum value along the X-axis.
	 */
	public double getXMin()
	{
		return xmin;
	}
	
	/**
	 * @return the maximum value along the X-axis.
	 */
	public double getXMax()
	{
		return xmax;
	}
	
	/**
	 * @return the minimum value along the Y-axis.
	 */
	public double getYMin()
	{
		return ymin;
	}
	
	/**
	 * @return the maximum value along the Y-axis.
	 */
	public double getYMax()
	{
		return ymax;
	}
	
}
