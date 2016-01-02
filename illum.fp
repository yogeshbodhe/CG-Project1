/*
 * Illumination fragment shader: phong and toon shading
 */

// From vertex shader: position and normal (in eye coordinates)
varying vec4 pos;
varying vec3 norm;

// Do Phong specular shading (r DOT v) instead of Blinn-Phong (n DOT h)
uniform int phong;
// Do toon shading
uniform int toon;
// If false, then don't do anything in fragment shader
uniform int useFragShader;

// Toon shading parameters
uniform float toonHigh;
uniform float toonLow;

// Apply volume texture to diffuse term
//uniform int volTexture;
// Volume texture scale
//uniform float volRes;

// Compute toon shade value given diffuse and specular component levels
vec4 toonShade(float diffuse, float specular)
{
    if(specular > toonHigh){
    	return vec4(1.0, 1.0, 1.0, 1.0);
    }
    
    if(diffuse > toonHigh){
    	diffuse = 0.8;
    }
    else if(diffuse < toonLow){
    	diffuse = 0.2;
    }
    else{
    	diffuse = 0.5;
    }
    
    return (gl_FrontLightProduct[0].ambient + (gl_FrontLightProduct[0].diffuse * diffuse));
}

void main()
{
    if (useFragShader == 0) {
        // Pass through
        gl_FragColor = gl_Color;
    } else {
        // Do lighting computation
        
        vec4 l = gl_LightSource[0].position - pos;
    	l = normalize(l);
    	
    	vec4 v = -pos;
    	v = normalize(v);
    
    	vec4 n = vec4(normalize(norm), 0.0);
    
    	float nl = dot(n, l);
    	float diffuse = max(0.0, nl);
    	diffuse = clamp(diffuse, 0.0, 1.0);
    
    	float specular = 0.0;
    
    	if(nl >= 0.0){
    		if(phong == 1){
    			vec4 r = reflect(-l, n);
    			r = normalize(r);
    			
    			float rv = dot(r, v);
    			rv = max(0.0, rv);
    			
    			specular = pow(rv, gl_FrontMaterial.shininess / 2.0);
	    		specular = clamp(specular, 0.0, 1.0);
    		}
    		else{
	    		vec4 h = l + v;
	    		h = normalize(h);
	    	
	    		float nh = dot(n, h);
	    		nh = pow(nh, gl_FrontMaterial.shininess);
	    	
	    		specular = max(0.0, nh);
	    		specular = clamp(specular, 0.0, 1.0);
	    	}
    	}
        
        if (toon == 1) {
            gl_FragColor = toonShade(diffuse, specular);
        } else {
            gl_FragColor = gl_FrontLightProduct[0].ambient + (gl_FrontLightProduct[0].diffuse * diffuse) + (gl_FrontLightProduct[0].specular * specular);
        }
    }
}
