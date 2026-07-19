#version 120

uniform vec2 size;
uniform vec4 color;
uniform vec4 radius;

float roundSDF(vec2 CenterPosition, vec2 Size, vec4 Radius) {
    Radius.xy = (CenterPosition.x>0.0)?Radius.xy : Radius.zw;
    Radius.x  = (CenterPosition.y>0.0)?Radius.x  : Radius.y;
    
    vec2 q = abs(CenterPosition)-Size+Radius.x;
    return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - Radius.x;
}

void main() {
    vec2 rectHalf = size * .5;
    float smoothedAlpha =  (1.0-smoothstep(0.0, 1.0, roundSDF(rectHalf - (gl_TexCoord[0].st * size), rectHalf - 0.5, radius))) * color.a;
    gl_FragColor = vec4(color.rgb, smoothedAlpha);
}
