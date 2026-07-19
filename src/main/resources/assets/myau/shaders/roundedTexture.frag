#version 120

uniform vec2 size;
uniform sampler2D texture;
uniform float radius;

float roundSDF(vec2 p, vec2 b, float r) {
	return length(max(abs(p) - b, 0.0)) - r;
}

void main() {
    vec2 rectHalf = size * .5;
    float smoothedAlpha =  (1.0-smoothstep(0.0, 1.0, roundSDF(rectHalf - (gl_TexCoord[0].st * size), rectHalf - radius - 0.5, radius))) * texture2D(texture, gl_TexCoord[0].st).a;
    gl_FragColor = vec4(texture2D(texture, gl_TexCoord[0].st).rgb, smoothedAlpha);
}
