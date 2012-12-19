/**
 * 
 */
package org.biojava3.structure.quaternary.jmolScript;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Color4f;
import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.biojava3.structure.quaternary.core.AxisTransformation;
import org.biojava3.structure.quaternary.core.Rotation;
import org.biojava3.structure.quaternary.core.RotationGroup;
import org.biojava3.structure.quaternary.core.Subunits;
import org.biojava3.structure.quaternary.geometry.Polyhedron;
import org.biojava3.structure.quaternary.misc.ColorBrewer;


/**
 * @author Peter
 *
 */
public abstract class JmolSymmetryScriptGenerator {
	private static String N_FOLD_AXIS_COLOR = "red";
	private static String TWO_FOLD_AXIS_COLOR = "deepskyblue";
	private static String THREE_FOLD_AXIS_COLOR = "lime";

	private static double AXIS_SCALE_FACTOR = 1.2;
	
	private AxisTransformation axisTransformation = null;
	private RotationGroup rotationGroup = null;
	private Polyhedron polyhedron = null;
	
	
	public JmolSymmetryScriptGenerator(AxisTransformation axisTransformation) {
		this.axisTransformation = axisTransformation;
		this.rotationGroup = axisTransformation.getRotationGroup();
	}
	
	/**
	 * Returns an instance of a JmolSymmetryScriptGenerator, based on the point group of a structure (factory method)
	 * @param axisTransformation
	 * @param rotationGroup
	 * @return instance of JmolSymmetryScriptGenerator
	 */
	public static JmolSymmetryScriptGenerator getInstance(AxisTransformation axisTransformation) {
		String pointGroup = axisTransformation.getRotationGroup().getPointGroup();
		if (pointGroup.equals("C1")) {
			return new JmolSymmetryScriptGeneratorC1(axisTransformation);
		} else if (pointGroup.startsWith("C")) {
			return new JmolSymmetryScriptGeneratorCn(axisTransformation);
		} else if (pointGroup.startsWith("D")) {
			return new JmolSymmetryScriptGeneratorDn(axisTransformation);
		} else if (pointGroup.equals("T")) {
			return new JmolSymmetryScriptGeneratorT(axisTransformation);
		} else if (pointGroup.equals("O")) {
			return new JmolSymmetryScriptGeneratorO(axisTransformation);
		} else if (pointGroup.equals("I")) {
			return new JmolSymmetryScriptGeneratorI(axisTransformation);
		} 
		
		return null;
	}
	
	/**
	 * Returns the Jmol zoom to fit polyhedron and symmetry axes. This zoom
	 * level should be used so that the polyhedron and symmetry axes are not cutoff.
	 * @return
	 */
	abstract public int getZoom();
	
	/**
	 * Returns a Jmol script to set the default orientation for a structure
	 * @return Jmol script
	 */
	public String getDefaultOrientation() {	
		StringBuilder s = new StringBuilder();
		s.append(setCentroid());
		
		// calculate  orientation
		Matrix3d m = polyhedron.getViewMatrix(0);
		m.mul(axisTransformation.getRotationMatrix());
		Quat4d q = new Quat4d();
		q.set(m);
		
		// set orientation
		s.append("moveto 0 quaternion{");
		s.append(jMolFloat(q.x));
		s.append(",");
		s.append(jMolFloat(q.y));
		s.append(",");
		s.append(jMolFloat(q.z));
		s.append(",");
		s.append(jMolFloat(q.w));
		s.append("};");
		return s.toString();
	}
	
	/**
	 * Returns the number of orientations available for this structure
	 * @return number of orientations
	 */
	public int getOrientationCount() {
		return polyhedron.getViewCount();
	}
	
	/**
	 * Returns a Jmol script that sets a specific orientation
	 * @param index orientation index
	 * @return Jmol script
	 */
	public String getOrientation(int index) {	
		StringBuilder s = new StringBuilder();
		s.append(setCentroid());
		
		// calculate  orientation
		Matrix3d m = polyhedron.getViewMatrix(index);
		m.mul(axisTransformation.getRotationMatrix());
		Quat4d q = new Quat4d();
		q.set(m);
		
		// set orientation
		s.append("moveto 4 quaternion{");
		s.append(jMolFloat(q.x));
		s.append(",");
		s.append(jMolFloat(q.y));
		s.append(",");
		s.append(jMolFloat(q.z));
		s.append(",");
		s.append(jMolFloat(q.w));
		s.append("}");
		s.append(";");
		return s.toString();
	}
	
	/**
	 * Returns a Jmol script that sets a specific orientation and zoom
	 * to draw either axes or polyhedron
	 * @param index orientation index
	 * @return Jmol script
	 */
	public String getOrientationWithZoom(int index) {
		StringBuilder s = new StringBuilder();
		s.append(getOrientation(index));
		s.insert(s.length()-1, getZoom());
		return s.toString();
		
	}
	/**
	 * Returns the name of a specific orientation
	 * @param index orientation index
	 * @return name of orientation
	 */
	public String getOrientationName(int index) {	
	    return polyhedron.getViewName(index);
	}

	/**
	 * Returns a Jmol script that draws an invisible polyhedron around a structure.
	 * Use showPolyhedron() and hidePolyhedron() to toggle visibility.
	 * @return Jmol script
	 */
	public String drawPolyhedron() {
		StringBuilder s = new StringBuilder();

		Point3d[] vertices = getPolyhedronVertices();
		
		int index = 0;
		double width = getMaxExtension()*0.015;

		for (int[] lineLoop: polyhedron.getLineLoops()) {
			s.append("draw polyhedron");
			s.append(index++);
			s.append(" line");
			for (int i: lineLoop) {
				s.append(getJmolPoint(vertices[i]));
			}
			s.append("width ");
		    s.append(fDot2(width));
			s.append(" color");
			Color4f c = getPolyhedronColor();
			s.append(getJmolColor(c));
			s.append(" off;");
		}

		return s.toString();
	}

	
	
	public String hidePolyhedron() {
		return "draw polyhedron* off;";
	}
	
	public String showPolyhedron() {
		return "draw polyhedron* on;";
	}
	
	/**
	 * Returns a Jmol script that draws symmetry or inertia axes for a structure.
	 * Use showAxes() and hideAxes() to toggle visibility.
	 * @return Jmol script
	 */
	public String drawAxes() {
		if (rotationGroup.getPointGroup().equals("C1")) {
			return drawInertiaAxes();
		} else {
			return drawSymmetryAxes();
		}
	}
	
	/**
	 * Returns a Jmol script to hide axes
	 * @return Jmol script
	 */
	public String hideAxes() {
		return "draw axes* off;";
	}
	
	/**
	 * Returns a Jmol script to show axes
	 * @return Jmol script
	 */
	public String showAxes() {
		return "draw axes* on;";
	}
	
	/**
	 * Returns a Jmol script that displays a symmetry polyhedron and symmetry axes
	 * and then loop through different orientations
	 * @return Jmol script
	 */
	public String playOrientations() {
		StringBuilder s = new StringBuilder();
		
		// draw point group
		s.append(drawFooter("Point group" + rotationGroup.getPointGroup(), "white"));
		
		// draw polygon
		s.append(drawPolyhedron()); // draw invisibly
		s.append(showPolyhedron());
			
		// draw axes
		s.append(drawAxes());
		s.append(showAxes());
		
		// loop over all orientations with 4 sec. delay
		for (int i = 0; i < getOrientationCount(); i++) {
			s.append(deleteHeader());
			s.append(getOrientationWithZoom(i));
			s.append(drawHeader(polyhedron.getViewName(i), "white"));
			s.append("delay 4;");
		}
		
		// go back to first orientation
		s.append(deleteHeader());
		s.append(getOrientationWithZoom(0));
		s.append(drawHeader(polyhedron.getViewName(0), "white"));
		
		return s.toString();
	}	
	
	/**
	 * Returns a Jmol script that colors the subunits of a structure by different colors
	 * @return
	 */
	public String colorBySubunit() {
	    Subunits subunits = axisTransformation.getSubunits();
	    List<Integer> modelNumbers = subunits.getModelNumbers();
	    List<String> chainIds = subunits.getChainIds();
	    List<List<Integer>> orbits = axisTransformation.getOrbitsByZDepth();
		int fold = rotationGroup.getRotation(0).getFold();

		Color4f[] colors = null;
		if (fold > 1) {
	        colors = ColorBrewer.Spectral.getColor4fPalette(2*fold);
		} else {
			colors = ColorBrewer.Spectral.getColor4fPalette(orbits.size());
		}
		int half = colors.length/2;
		for (int i = 0; i < half; i++) {
			if (i % 2 == 1) {
			   Color4f temp = colors[i];
			   colors[i] = colors[half+i];
			   colors[half+i] = temp;
			}
		}
	    Map<Color4f, List<String>> colorMap = new HashMap<Color4f, List<String>>();
	    
		for (int i = 0; i < orbits.size(); i++) {
			for (int j = 0; j < fold; j++) {
				// assign alternating color sets to adjacent orbits
				int colorIndex = i;
				if (fold > 1) {
					if (i % 2 == 0) {
						colorIndex = j;
					} else {
						colorIndex = fold + j;
					}
				}
				int subunit = orbits.get(i).get(j);
				Color4f c = colors[colorIndex];
				List<String> ids = colorMap.get(c);
				if (ids == null) {
					ids = new ArrayList<String>();
					colorMap.put(c,  ids);
				}
				String id = chainIds.get(subunit) + "/" + (modelNumbers.get(subunit)+1);
				ids.add(id);
			}
		}
		return getJmolColorScript(colorMap);
	}
	
	/**
	 * Returns a Jmol script that colors subunits by their sequence cluster ids.
	 * @return Jmol script
	 */
	public String colorBySequenceCluster() {
	    Subunits subunits = axisTransformation.getSubunits();
	    int n = subunits.getSubunitCount();
	    List<Integer> modelNumbers = subunits.getModelNumbers();
	    List<String> chainIds = subunits.getChainIds();
	    List<Integer> seqClusterIds = subunits.getSequenceClusterIds();
	    int clusters = Collections.max(seqClusterIds) + 1;
	    Color4f[] colors = ColorBrewer.BrBG.getColor4fPalette(clusters);
		Map<Color4f, List<String>> colorMap = new HashMap<Color4f, List<String>>();
		
		for (int i = 0; i < n; i++) {
			Color4f c = colors[seqClusterIds.get(i)];
			List<String> ids = colorMap.get(c);
			if (ids == null) {
				ids = new ArrayList<String>();
				colorMap.put(c,  ids);
			}
			String id = chainIds.get(i) + "/" + (modelNumbers.get(i)+1);
			ids.add(id);

		}
		return getJmolColorScript(colorMap);
	}
	
	/**
	 * Returns a Jmol script that colors subunits to highlight the symmetry within a structure
	 * @return Jmol script
	 */
	public String colorBySymmetry() {
		// TODO needs some refactoring
		String pointGroup = rotationGroup.getPointGroup();
		Subunits subunits = axisTransformation.getSubunits();
		List<Integer> modelNumbers = subunits.getModelNumbers();
		List<String> chainIds = subunits.getChainIds();
		List<List<Integer>> orbits = axisTransformation.getOrbitsByZDepth();

		int n = subunits.getSubunitCount();
		int fold = rotationGroup.getRotation(0).getFold();
		
		Map<Color4f, List<String>> colorMap = new HashMap<Color4f, List<String>>();

		// Simple Cn symmetry
		if (pointGroup.startsWith("C") && n == fold) {
			colorMap = getCnColorMap();
			// complex cases
		} else if ((pointGroup.startsWith("D") && orbits.size() > 2) || 
				pointGroup.equals("T")|| pointGroup.equals("O") || pointGroup.equals("I")) {
			int nColor = 0;
			if (orbits.size() % 2 == 0) {
				nColor = orbits.size()/2;
			} else {
				nColor = (orbits.size() + 1)/2;
			}
			Color4f[] colors = getSymmetryColors(nColor); 

			for (int i = 0; i < orbits.size(); i++) {
				int colorIndex = i;
				if (i >= nColor) {
					colorIndex = orbits.size() - 1 - i;
				}
				Color4f c = new Color4f(colors[colorIndex]);
				List<String> ids = colorMap.get(c);
				if (ids == null) {
					ids = new ArrayList<String>();
					colorMap.put(c,  ids);
				}
				for (int subunit: orbits.get(i)) {
					String id = chainIds.get(subunit) + "/" + (modelNumbers.get(subunit)+1);
					ids.add(id);
				}
			}

			// Simple Dn symmetry
		} else {
			Color4f[] colors = getSymmetryColors(orbits.size());
			
			for (int i = 0; i < orbits.size(); i++) {
				Color4f c = new Color4f(colors[i]);
				List<String> ids = colorMap.get(c);
				if (ids == null) {
					ids = new ArrayList<String>();
					colorMap.put(c,  ids);
				}
				List<Integer> orbit = orbits.get(i);
				for (int j = 0; j < orbit.size(); j++) {
					String id = chainIds.get(orbit.get(j)) + "/" + (modelNumbers.get(orbit.get(j))+1);
					ids.add(id);
				}
			}
		}
		return getJmolColorScript(colorMap);
	}
	
	private Map<Color4f, List<String>> getCnColorMap() {
		Subunits subunits = axisTransformation.getSubunits();
		List<Integer> modelNumbers = subunits.getModelNumbers();
		List<String> chainIds = subunits.getChainIds();
		List<List<Integer>> orbits = axisTransformation.getOrbitsByZDepth();

		int n = subunits.getSubunitCount();
		int fold = rotationGroup.getRotation(0).getFold();

		Map<Color4f, List<String>> colorMap = new HashMap<Color4f, List<String>>();


		Color4f[] colors = getSymmetryColors(n);
//		int refSubunit = axisTransformation.getReferenceSubunit();
		List<Integer> orbit = orbits.get(0);
		axisTransformation.alignWithReferenceAxis(orbit);
//		for (int i = 0; i < n; i++) {
//			if (orbit.get(0) != refSubunit) {
//				Collections.rotate(orbit,1);
//			} else {
//				break;
//			}
//		}
		for (int i = 0; i < n; i++) {
			int subunit = orbits.get(0).get(i);
			String id = chainIds.get(subunit) + "/" + (modelNumbers.get(subunit)+1);
			List<String> ids = Collections.singletonList(id);
			colorMap.put(colors[i], ids);
		}

		return colorMap;
	}
	
	// --- protected methods ---
	/**
	 * Returns the maximum extension (length) of structure
	 * @return
	 */
	protected double getMaxExtension() {
		Vector3d dimension = axisTransformation.getDimension();
		double maxExtension = Math.max(dimension.x, dimension.y);
		maxExtension = Math.max(maxExtension, dimension.z);
		return maxExtension;
	}
	
	/**
	 * Returns the mean extension (length) of structure
	 * @return
	 */
	protected double getMeanExtension() {
		Vector3d dimension = axisTransformation.getDimension();
		return (dimension.x+dimension.y+dimension.z)/3;
	}
	
	/**
	 * @return the axisTransformation
	 */
	protected AxisTransformation getAxisTransformation() {
		return axisTransformation;
	}

	/**
	 * @param axisTransformation the axisTransformation to set
	 */
	protected void setAxisTransformation(AxisTransformation axisTransformation) {
		this.axisTransformation = axisTransformation;
	}

	/**
	 * @return the rotationGroup
	 */
	protected RotationGroup getRotationGroup() {
		return rotationGroup;
	}

	/**
	 * @param rotationGroup the rotationGroup to set
	 */
	protected void setRotationGroup(RotationGroup rotationGroup) {
		this.rotationGroup = rotationGroup;
	}

	/**
	 * @return the polyhedron
	 */
	protected Polyhedron getPolyhedron() {
		return polyhedron;
	}

	/**
	 * @param polyhedron the polyhedron to set
	 */
	protected void setPolyhedron(Polyhedron polyhedron) {
		this.polyhedron = polyhedron;
	}

//  --- private methods ---
	private Point3d[] getPolyhedronVertices() {
		Point3d[] vertices = polyhedron.getVertices();
		Matrix4d reverseTransformation = axisTransformation.getGeometicCenterTransformation();
		for (int i = 0; i < vertices.length; i++) {
			reverseTransformation.transform(vertices[i]);
		}
		return vertices;
	}
	
	private String getJmolColorScript(Map<Color4f, List<String>> map) {
		StringBuilder s = new StringBuilder();
		for (Entry<Color4f, List<String>> entry: map.entrySet()) {
			s.append("select ");
			List<String> ids = entry.getValue();
			for (int i = 0; i < ids.size(); i++) {
				s.append("*:");
				s.append(ids.get(i));
				if (i < ids.size() -1 ) {
				    s.append(",");
				} else {
					s.append(";");
				}
			}
			s.append("color cartoon");	
			s.append(getJmolColor(entry.getKey()));
			s.append(";");
			s.append("color atom");
			s.append(getJmolColor(entry.getKey()));
			s.append(";");
			
		}
		return s.toString();
	}
	/**
	 * Return a color that is complementary to the symmetry color
	 * @return
	 */
	private Color4f getPolyhedronColor() {
		Color4f[] colors = getSymmetryColors(5);
		Color4f strongestColor = colors[4];
		Color4f complement = new Color4f(Color.WHITE);
		complement.sub(strongestColor);
		return complement;
	}
	
	/**
	 * Returns a unique color palette based on point group
	 * @param nColors
	 * @return
	 */
	private Color4f[] getSymmetryColors(int nColors) {
		int offset = 0;
		int dMax = nColors + offset;
		String pointGroup = rotationGroup.getPointGroup();
		Color4f[] colors = null;
		if (pointGroup.equals("C1")) {
	//		offset = 1;
	//		dMax = nColors + offset;
			colors = ColorBrewer.Greys.getColor4fPalette(dMax);
		} else if (pointGroup.startsWith("C")) {
			colors = ColorBrewer.YlGnBu.getColor4fPalette(dMax);		
		} else if (pointGroup.startsWith("D")) {
			colors = ColorBrewer.YlOrRd.getColor4fPalette(dMax);
		} else if (pointGroup.equals("T")) {
			colors = ColorBrewer.Greens.getColor4fPalette(dMax);
		} else if (pointGroup.equals("O")) {
			colors = ColorBrewer.Blues.getColor4fPalette(dMax);
		} else if (pointGroup.equals("I")) {
			colors = ColorBrewer.BuPu.getColor4fPalette(dMax);
		} else {
			colors = ColorBrewer.Greys.getColor4fPalette(dMax);
		}
		System.arraycopy(colors, offset, colors, 0, dMax-offset);
		return colors;
		
	}
	
	private String drawInertiaAxes() {
		StringBuilder s = new StringBuilder();
		Point3d centroid = axisTransformation.getGeometricCenter();
		Vector3d[] axes = axisTransformation.getPrincipalAxesOfInertia();

		for (int i = 0; i < axes.length; i++) {
			s.append("draw axesInertia");
			s.append(i);
			s.append(" ");
			s.append("line");
			Point3d v1 = new Point3d(axes[i]);
			if (i == 0) {
				v1.scale(AXIS_SCALE_FACTOR*axisTransformation.getDimension().y);
			} else if (i == 1) {
				v1.scale(AXIS_SCALE_FACTOR*axisTransformation.getDimension().x);
			} else if (i == 2) {
				v1.scale(AXIS_SCALE_FACTOR*axisTransformation.getDimension().z);
			}
			Point3d v2 = new Point3d(v1);
			v2.negate();
			v1.add(centroid);
			v2.add(centroid);
			s.append(getJmolPoint(v1));
			s.append(getJmolPoint(v2));
			s.append("width 0.5 ");
			s.append(" color white");
			s.append(" off;");
		}
        return s.toString();
	};
	
	private String drawSymmetryAxes() {
		StringBuilder s = new StringBuilder();

		int n = rotationGroup.getOrder();
		if (n == 0) {
			return s.toString();
		}

		float diameter = 0.5f;
		double radius = 0;
		String color = "";

		List<Rotation> axes = getUniqueAxes();
//		System.out.println("Unique axes: " + axes.size());
		int i = 0;
		for (Rotation r: axes) {
			if (rotationGroup.getPointGroup().startsWith("C") || (rotationGroup.getPointGroup().startsWith("D") && r.getDirection() == 0)) {
				radius =  axisTransformation.getDimension().z; // principal axis uses z-dimension
				color = N_FOLD_AXIS_COLOR;
			} else {
				radius = polyhedron.getCirumscribedRadius();
			
				if (r.getFold() == 2) {
					color = TWO_FOLD_AXIS_COLOR;
				} else if (r.getFold() == 3) {
					color = THREE_FOLD_AXIS_COLOR;
				} else {
					color = N_FOLD_AXIS_COLOR;
				}
			}
		

			Point3d center = axisTransformation.getGeometricCenter();
			AxisAngle4d axisAngle = r.getAxisAngle();
			Vector3d axis = new Vector3d(axisAngle.x, axisAngle.y, axisAngle.z);
			Vector3d refAxis = axisTransformation.getRotationReferenceAxis();
			
//			System.out.println("Unique axes: " + axis + " n: " + r.getFold());
			s.append(getSymmetryAxis(i, i+axes.size(), rotationGroup.getPointGroup(), r.getFold(), refAxis, radius, diameter, color, center, axis));
	        i++;
		}

		return s.toString();
	}

	private Vector3d getAligmentVector(Point3d point, Vector3d axis) {		
		// for system with a single Cn axis
		if (rotationGroup.getPointGroup().startsWith("C") || rotationGroup.getPointGroup().startsWith("D")) {
			// if axis is orthogonal to principal axis, use principal axis as reference axis
			if (axis.dot(axisTransformation.getPrincipalRotationAxis()) < 0.1) {
				return axisTransformation.getPrincipalRotationAxis();
			} else {
				return axisTransformation.getRotationReferenceAxis();
			}
		}

		// for T, O, and I point groups find reference axis with respect to
		// nearest polyhedron vertex
		Vector3d ref = new Vector3d();
		double dSqThreshold = 25;
		double dSqMin = Double.MAX_VALUE;
		Point3d refPoint = null;
		// find nearest point on polyhedron as reference point,
		// but do not choose a point on the same axis (therefore, we 
		// apply a distance threshold squared 5A*5A = 25A^2
		for (Point3d v: getPolyhedronVertices()) {
			double dSq = point.distanceSquared(v);
			if (dSq > dSqThreshold) {
				if (dSq < dSqMin) {
					dSqMin = dSq;
					refPoint = v;
				}
			}
		}


		ref.sub(point, refPoint);

		// this ref vector is usually not orthogonal to the 
		// axis. The following double-cross product makes it
		// orthogonal.
		ref.cross(axis, ref);
		ref.cross(axis, ref); // note, done twice on purpose
		ref.normalize();
		return ref;
	}
	
	private String getSymmetryAxis(int i, int j, String pointGroup, int n, Vector3d referenceAxis, double radius, float diameter, String color, Point3d center, Vector3d axis) {
		boolean drawPolygon = true;
		
		Point3d p1 = new Point3d(axis);
		p1.scaleAdd(-AXIS_SCALE_FACTOR * radius, center);

		Point3d p2 = new Point3d(axis);
		p2.scaleAdd(AXIS_SCALE_FACTOR * radius, center);

		StringBuilder s = new StringBuilder();
		s.append("draw");
		s.append(" axesSymmetry");
		s.append(i);
		s.append(" cylinder");
		s.append(getJmolPoint(p1));
		s.append(getJmolPoint(p2));
		s.append("diameter ");
		s.append(diameter);
		s.append(" color ");
		s.append(color);
		s.append(" off;");

		// calc. point to center symmetry symbols. They are offset by 0.01
		// to avoid overlap with the polyhedron
		p1 = new Point3d(axis);
		p1.scaleAdd(-1.01*radius, center);

		p2 = new Point3d(axis);
		p2.scaleAdd(1.01*radius, center);

		if (drawPolygon == true) {
		
//			double polygonRadius = getMaxExtension() * 0.06;
			double polygonRadius = getMeanExtension() * 0.06;
			if (n == 2) {
				referenceAxis = getAligmentVector(p1, axis);
				s.append(getC2PolygonJmol(i, p1, referenceAxis, axis, n, color, polygonRadius));
				referenceAxis = getAligmentVector(p2, axis);
				s.append(getC2PolygonJmol(j, p2,  referenceAxis, axis, n, color, polygonRadius));
			} else if (n > 2) {
				referenceAxis = getAligmentVector(p1, axis);
				s.append(getPolygonJmol(i, p1, referenceAxis, axis, n, color, polygonRadius));
				referenceAxis = getAligmentVector(p2, axis);
				s.append(getPolygonJmol(j, p2, referenceAxis, axis, n, color, polygonRadius));
			}
		}

		return s.toString();
	}
	
	private static String getPolygonJmol(int index, Point3d center, Vector3d referenceAxis, Vector3d axis, int n, String color, double radius) {
		StringBuilder s = new StringBuilder();
		s.append("draw axesSymbol");
		s.append(index);
		s.append(" ");
		s.append("polygon");
		s.append(" ");
		s.append(n+1); 
		s.append(getJmolPoint(center));

		Vector3d[] vertexes = getPolygonVertices(axis, referenceAxis, center, n, radius);
		// create vertex list
		for (Vector3d v: vertexes) {
			s.append(getJmolPoint(v));
		}

		// create face list
		s.append(n);
		for (int i = 1; i <= n; i++) {
			s.append("[");
			s.append(0);
			s.append(" ");
			s.append(i);
			s.append(" ");
			if (i < n) {
				s.append(i+1);
			} else {
				s.append(1);
			}
			s.append(" ");
			s.append(7);
			s.append("]");
		}

		if (n == 2) {
	      	s.append("mesh off");
		}
		s.append(" color ");
		s.append(color);
		s.append(" off;");

		return s.toString();
	}
	
	private static Vector3d[] getPolygonVertices(Vector3d axis, Vector3d referenceAxis, Point3d center, int n, double radius) {
		Vector3d ref = new Vector3d(referenceAxis);
		ref.scale(radius);		

		AxisAngle4d axisAngle = new AxisAngle4d(axis, 0);
		Vector3d[] vectors = new Vector3d[n];
		Matrix4d m = new Matrix4d();

		for (int i = 0; i < n; i++) {
			axisAngle.angle = i * 2 * Math.PI/n;
			vectors[i] = new Vector3d(ref);		
			m.set(axisAngle);
			m.transform(vectors[i]);
			vectors[i].add(center);
		}
		return vectors;
	}
	
	private static String getC2PolygonJmol(int index, Point3d center, Vector3d referenceAxis, Vector3d axis, int n, String color, double radius) {
		StringBuilder s = new StringBuilder();
		n = 10;
		s.append("draw axesSymbol");
		s.append(index);
		s.append(" ");
		s.append("polygon");
		s.append(" ");
		s.append(n+1-2); 
		s.append(getJmolPoint(center));

		Vector3d[] vertexes = getC2PolygonVertices(axis, referenceAxis, center, n, radius);
		// create vertex list
		for (Vector3d v: vertexes) {
			s.append(getJmolPoint(v));
		}
		 
		// there are two vertices less, since the first and last vertex of
		// the two arcs are identical
		n -= 2;

		// create face list
		s.append(n);

		for (int i = 1; i <= n; i++) {
			s.append("[");
			s.append(0);
			s.append(" ");
			s.append(i);
			s.append(" ");
			if (i < n) {
				s.append(i+1);
			} else {
				s.append(1);
			}
			s.append(" ");
			s.append(7);
			s.append("]");
		}

		s.append("color ");
		s.append(color);
		s.append(" off;");

		return s.toString();
	}
	private static Vector3d[] getC2PolygonVertices(Vector3d axis, Vector3d referenceAxis, Point3d center, int n, double radius) {
		Vector3d ref = new Vector3d(referenceAxis);
		ref.scale(4*radius);		

		AxisAngle4d axisAngle = new AxisAngle4d(axis, 0);
		int k = n / 2;
		// draw arc 1/6 of a full circle
		int f = 6;
		Vector3d[] vectors = new Vector3d[n-2];	
		Matrix4d m = new Matrix4d();
		
		// first point of arc
		axisAngle.angle = (k+0.5) * 2 * Math.PI/(f*k);
		Vector3d begin = new Vector3d(ref);		
		m.set(axisAngle);
		m.transform(begin);
		
		// last point of arc
		axisAngle.angle = (2*k-1+0.5) * 2 * Math.PI/(f*k);
		Vector3d end = new Vector3d(ref);		
		m.set(axisAngle);
		m.transform(end);
		
		// center of arc
		Vector3d arcCenter = new Vector3d();
		arcCenter.interpolate(begin, end, 0.5);
		arcCenter.negate();
		
		// add translation component
		Vector3d trans =  new Vector3d();
		trans.sub(center, arcCenter);

		// draw arc (1/6 of a full circle)
		for (int i = 0; i < k; i++) {
			axisAngle.angle = (k + i + 0.5) * 2 * Math.PI/(f*k);
			vectors[i] = new Vector3d(ref);		
			m.set(axisAngle);
			m.transform(vectors[i]);
			vectors[i].add(arcCenter);
			vectors[i].add(center);
		}
		// in reverse order, draw reflected half of arc (1/6 of full circle)
		// don't draw first and last element, since the are already part of the previous arc
		for (int i = k; i < 2*k-2; i++) {
			axisAngle.angle = (f/2*k + i + 1.5) * 2 * Math.PI/(f*k);
			vectors[i] = new Vector3d(ref);		
			m.set(axisAngle);
			m.transform(vectors[i]);
			vectors[i].sub(arcCenter);
			vectors[i].add(center);
		}
		return vectors;
	}
	

    private List<Rotation> getUniqueAxes() {
    	List<Rotation> uniqueRotations = new ArrayList<Rotation>();
    	
    	for (int i = 0, n = rotationGroup.getOrder(); i < n; i++) {
			Rotation rotationI = rotationGroup.getRotation(i);
			AxisAngle4d axisAngleI = rotationI.getAxisAngle();
			Vector3d axisI = new Vector3d(axisAngleI.x, axisAngleI.y, axisAngleI.z);
			
			boolean redundant = false;
			for (Rotation r: uniqueRotations) {
				AxisAngle4d axisAngleJ = r.getAxisAngle();
			    Vector3d axisJ = new Vector3d(axisAngleJ.x, axisAngleJ.y, axisAngleJ.z);
			    if (Math.abs(axisI.dot(axisJ)) > 0.99) {
			    	redundant = true;
			    	break;
			    }
			}
			
			if (! redundant) {
				uniqueRotations.add(rotationI);
			}
    	}
        return uniqueRotations;
    }
	
	private String drawHeader(String text, String color) {
		StringBuilder s = new StringBuilder();
		s.append("set echo top center;");
		s.append("color echo ");
		s.append(color);
		s.append(";");
		s.append("font echo 24 sanserif;");
		s.append("echo ");
		s.append(text);
		s.append(";");
		return s.toString();
	}
	
	private String deleteHeader() {
		return "set echo top center;echo ;";
	}
	
	private String drawFooter(String text, String color) {
		StringBuilder s = new StringBuilder();
		s.append("set echo bottom center;");
		s.append("color echo ");
		s.append(color);
		s.append(";");
		s.append("font echo 24 sanserif;");
		s.append("echo Point group ");
		s.append(rotationGroup.getPointGroup());
		s.append(";");
		return s.toString();
	}
	
	private String setCentroid() {
		// calculate center of rotation
	//	Point3d centroid = axisTransformation.getGeometricCenter();
		Point3d centroid = axisTransformation.getCentroid();
			
		// set centroid
		StringBuilder s = new StringBuilder();
		s.append("center");
		s.append(getJmolPoint(centroid));
		s.append(";");
		return s.toString();
	}
	
	private static String getJmolPoint(Tuple3d point) {
		StringBuilder s = new StringBuilder();
		s.append("{");
		s.append(fDot2(point.x));
		s.append(",");
		s.append(fDot2(point.y));
		s.append(",");
		s.append(fDot2(point.z));
		s.append("}");
		return s.toString();
	}
	
	private static String getJmolColor(Color4f color) {
		StringBuilder s = new StringBuilder();
		s.append("{");
		s.append(f1Dot2(color.x));
		s.append(",");
		s.append(f1Dot2(color.y));
		s.append(",");
		s.append(f1Dot2(color.z));
		s.append("}");
		return s.toString();
	}
	
	private static String f1Dot2(float number) {
		return String.format("%1.2f", number);
	}
	
	private static String fDot2(double number) {
		return String.format("%.2f", number);
	}
	
	/**
	 * Returns a lower precision floating point number for Jmol
	 * @param f
	 * @return
	 */
	private static float jMolFloat(double f) {
		if (Math.abs(f) < 1.0E-7) {
			return 0.0f;
		}
		return (float)f;
	}
	
}