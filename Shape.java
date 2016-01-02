/* class Shape
 * Abstract class for representing shapes using polygon meshes
 * The mesh is a list of vertices and polygons; its view is controlled
 * with some transformation parameters for M = T Rx Ry Rz S
 * The appearance is controlled by display options
 * Drawing methods using OpenGL are also included
 * 
 */

import java.awt.*;
import java.util.*;

import javax.media.opengl.GL;
import javax.vecmath.*;

public abstract class Shape
{
    // Polygon mesh: vertex and polygon lists
    Vertex[] vertices;
    Polygon[] polygons;

    // Name of shape
    public String name;

    // The GL shading program
    protected IllumProgram slProgram;

    // Parameters for specifying M; the 3D pose of shape
    private Vector<DoubleParameter> params;
    private DoubleParameter tx, ty, tz, rx, ry, rz, scale;

    // Parameters for material
    private DoubleParameter hue, kambi, kdiff, kspec, shiny;

    // Parameters for display options
    private Vector<BooleanParameter> options;
    private BooleanParameter drawPoly, drawWireframe, drawSmooth, drawSil;
    private BooleanParameter drawNormals;

    // Variable for telling GLSL to enable/disable the fragment shader
    // (so that you can draw wireframe, etc... with a passthru)
    int bindFragShade;

    // For adding polygons one by one
    private int lastPolygon;

    // ---------------------------------------------------------------------

    // Constructor: create empty mesh and parameters/options    
    public Shape(String shapeName)
    {
        name = shapeName;

        vertices = null;
        polygons = null;
        slProgram = null;
        lastPolygon = 0;

        params = new Vector<DoubleParameter>();
        options = new Vector<BooleanParameter>();

        tx = addParameter(new DoubleParameter("Tx", 0, -20, 20, 1));
        ty = addParameter(new DoubleParameter("Ty", 0, -20, 20, 1));
        tz = addParameter(new DoubleParameter("Tz", 0, -20, 20, 1));

        rx = addParameter(new DoubleParameter("Rx", 0, -180, 180, 1));
        ry = addParameter(new DoubleParameter("Ry", 0, -180, 180, 1));
        rz = addParameter(new DoubleParameter("Rz", 0, -180, 180, 1));

        scale = addParameter(new DoubleParameter("Scale", 1, 0.05, 10, 1));

        hue = addParameter(new DoubleParameter("Color", 0.1, 0.0, 1.0, 1));
        kambi = addParameter(new DoubleParameter("Ka", 0.2, 0.0, 1.0, 1));
        kdiff = addParameter(new DoubleParameter("Kd", 0.5, 0.0, 1.0, 1));
        kspec = addParameter(new DoubleParameter("Ks", 0.3, 0.0, 1.0, 1));
        shiny = addParameter(new DoubleParameter("Shininess", 40, 0, 128, 1));

        drawPoly = addOption(new BooleanParameter("Draw polygons", 
                                                  true, 1));
        drawSmooth = addOption(new BooleanParameter("Smooth shading", 
                                                    false, 1));
        drawWireframe = addOption(new BooleanParameter("Draw wireframe", 
                                                       false, 1));
        drawNormals = addOption(new BooleanParameter("Draw normals", 
                                                       false, 1));
        drawSil = addOption(new BooleanParameter("Draw silhouettes", 
                                                    false, 1));

        // Create Slang program
        slProgram = new IllumProgram("Illumination", this);
    }

    // For binding vertex/fragment shader's uniform variables
    // Does nothing by default
    protected void bindUniform(GL gl) { }

    // Keep track of list of all shape parameters/drawing options
    public DoubleParameter addParameter(DoubleParameter p)
    {
        params.add(p);
        return p;
    }
    public BooleanParameter addOption(BooleanParameter p)
    {
        options.add(p);
        return p;
    }

    // Accessors for parameters/options
    public Vector<DoubleParameter> getParams()
    {
        return params;
    }
    public Vector<BooleanParameter> getOptions()
    {
        return options;
    }

    // Get the material color -- using the hue color slider
    public float[] getMatColor()
    {
        Color hsv = Color.getHSBColor((float)hue.value, 1.0f, 1.0f);

        float matDiff[] = { 
            hsv.getRed()/255.f,
            hsv.getGreen()/255.f,
            hsv.getBlue()/255.f,
            1.0f
        };
        
        return matDiff;
    }

    // Reset all shape parameters to default values
    public void reset()
    {
        Iterator i;

        i = params.iterator(); 
        while (i.hasNext()) {
            ((Parameter)i.next()).reset();
        }

        i = options.iterator(); 
        while (i.hasNext()) {
            ((Parameter)i.next()).reset();
        }
    }

    // ---------------------------------------------------------------------

    // Add a new polygon to this mesh
    public void addPolygon(Polygon p)
    {
        polygons[lastPolygon++] = p;
    }

    // --------------------------------------------------------------------
    // GLSL enable/disable methods
    
    // True if GLSL should be used
    public boolean useGLSL()
    {
        return (slProgram.glslOn.value || 
                slProgram.phongModel.value ||
                slProgram.toonShading.value);
    }

    // Call to set up GLSL -- once each frame
    public void setupGLSL(GL gl)
    {
        if (useGLSL() && !slProgram.Ready()) {
            slProgram.init(gl);

	    if (slProgram.Ready()) {
		// bind only when the shading program is ready
		bindFragShade = gl.glGetUniformLocationARB(slProgram.program, 
							   "useFragShader");
      	    }
        }
        if ((slProgram.Ready() && useGLSL()) || !useGLSL()) {
            slProgram.enable(gl, useGLSL());
        }
    }

    // Call these to enable/disable the fragment shader (using the
    // useFragShader variable in illum.fp)
    public void enableFragShader(GL gl)
    {
        if (useGLSL() && slProgram.Ready()) {
            gl.glUniform1iARB(bindFragShade, 1);
        }
    }
    public void disableFragShader(GL gl)
    {
        if (useGLSL() && slProgram.Ready()) {
            gl.glUniform1iARB(bindFragShade, 0);
        }
    }

    // ---------------------------------------------------------------------
    // OpenGL drawing methods

    // Place light and create materials (based on slider input)
    public void setupScene(GL gl)
    {
        // Define light
        float white[]  = { 1, 1, 1, 1 };

        // Light position
        float lightPos[] = { 10, 20, 20, 1 };

        gl.glLightfv(GL.GL_LIGHT0, GL.GL_POSITION, lightPos, 0);
        gl.glLightfv(GL.GL_LIGHT0, GL.GL_AMBIENT,  white, 0);
        gl.glLightfv(GL.GL_LIGHT0, GL.GL_DIFFUSE,  white, 0);
        gl.glLightfv(GL.GL_LIGHT0, GL.GL_SPECULAR, white, 0);
        gl.glEnable(GL.GL_LIGHT0);

        // Define material
        float ka = (float)kambi.value,
              kd = (float)kdiff.value,
              ks = (float)kspec.value;

        // Get color from slider for setting diffuse
        float matrgb[] = getMatColor();

        float matAmbi[] = { ka, ka, ka, 1 };
        float matDiff[] = { kd*matrgb[0], kd*matrgb[1], kd*matrgb[2], 1 };
        float matSpec[] = { ks, ks, ks, 1 };

        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT,   matAmbi, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE,   matDiff, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR,  matSpec, 0);
        gl.glMaterialf( GL.GL_FRONT, GL.GL_SHININESS, (float)shiny.value);

        // Turn off global ambient light
        float globalAmbient[] = { 0, 0, 0, 0 };
        gl.glLightModelfv(GL.GL_LIGHT_MODEL_AMBIENT, globalAmbient, 0);

        // Make viewpoint local
        gl.glLightModeli(GL.GL_LIGHT_MODEL_LOCAL_VIEWER, GL.GL_TRUE);
    }

    // Draw scene
    public void draw(GL gl)
    {
        // Turn on GLSL (if available and necessary)
        setupGLSL(gl);

        // Set up light and materials
        setupScene(gl);

        // Apply M = T Rx Ry Rz S (most intuitive for Z-axis aligned objects)
        gl.glTranslated(tx.value, ty.value, tz.value);
        gl.glRotated(rx.value, 1, 0, 0);
        gl.glRotated(ry.value, 0, 1, 0);
        gl.glRotated(rz.value, 0, 0, 1);
        gl.glScaled(scale.value, scale.value, scale.value);

        // Draw polygons, wireframe, silhouette
        if(drawPoly.value){
        	enableFragShader(gl);
        	drawPolygons(gl);
        	disableFragShader(gl);
        }
        
        if(drawWireframe.value){
        	gl.glColor3d(0.0, 0.5, 0.0);
        	drawWireframe(gl);
        }
        
       
        if(drawSil.value){
        	gl.glColorMask(false, false, false, true);
        	drawPolygons(gl);
        	gl.glColorMask(true, true, true, true);
        	gl.glEnable(GL.GL_CULL_FACE);
        	gl.glLineWidth(2.0f);
    		gl.glColor3d(0.0, 0.0, 0.0);
    		gl.glPolygonMode(GL.GL_BACK, GL.GL_LINE);
    		gl.glCullFace(GL.GL_FRONT);
        	for(int i=0; i<polygons.length; i++){
        		gl.glBegin(GL.GL_POLYGON);
        		for(int j=0; j<polygons[i].size(); j++){
        			Point3d point = polygons[i].getVertex(j).getPoint();
        			gl.glVertex3d(point.x, point.y, point.z);
        		}
        		gl.glEnd();
        	}
        	gl.glDisable(GL.GL_CULL_FACE);
        }
        
        if(drawNormals.value){
        	gl.glColor3d(0.5, 0.5, 1.0);
        	gl.glLineWidth(1.0f);
        	drawNormals(gl);
        }
    }

    // Draw polygons in mesh (smooth shaded if drawSmooth option is true)
    private void drawPolygons(GL gl)
    {
    	gl.glEnable(GL.GL_LIGHTING);
		gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);
		gl.glPolygonOffset(1.0f, 1.0f);
        if (drawSmooth.value) {
	    // Smooth shading
    		for(int i=0; i<polygons.length; i++){
    			gl.glBegin(GL.GL_POLYGON);
        		for(int j=0; j<polygons[i].size(); j++){
        			Vertex vertex = polygons[i].getVertex(j);
        			Vector3d norm = vertex.getNormal();
        			gl.glNormal3d(norm.x, norm.y, norm.z);
        			Point3d point = vertex.getPoint();
        			gl.glVertex3d(point.x, point.y, point.z);
        		}
        		gl.glEnd();
        	}
	} else {
	    // Flat shading
		for(int i=0; i<polygons.length; i++){
			Vector3d norm = polygons[i].getNormal();
			gl.glBegin(GL.GL_POLYGON);
			gl.glNormal3d(norm.x, norm.y, norm.z);
    		for(int j=0; j<polygons[i].size(); j++){
    			Point3d point = polygons[i].getVertex(j).getPoint();
    			gl.glVertex3d(point.x, point.y, point.z);
    		}
    		gl.glEnd();
    	}
	}
        gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
		gl.glDisable(GL.GL_LIGHTING);
    }
    
    // Draw wireframe of mesh
    private void drawWireframe(GL gl)
    {
        // ...
    	gl.glLineWidth(1.0f);
		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_LINE);
    	for(int i=0; i<polygons.length; i++){
    		gl.glBegin(GL.GL_POLYGON);
    		for(int j=0; j<polygons[i].size(); j++){
    			Point3d point = polygons[i].getVertex(j).getPoint();
    			gl.glVertex3d(point.x, point.y, point.z);
    		}
    		gl.glEnd();
    	}
    }
    
    // Draw normals
    private void drawNormals(GL gl){
    	for(Vertex vertex:vertices){
    		gl.glBegin(GL.GL_LINES);
    		Point3d point = vertex.getPoint();
    		
    		gl.glVertex3d(point.x, point.y, point.z);
    		Vector3d norm = vertex.getNormal();
    		
    		double	x = point.x + norm.x * 0.1;
    		double	y = point.y + norm.y * 0.1;
    		double	z = point.z + norm.z * 0.1;
    		
    		gl.glVertex3d(x, y, z);
    		gl.glEnd();
    	}
    }
}
