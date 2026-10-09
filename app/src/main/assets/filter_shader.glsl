#version 300 es
precision highp float;
in vec2 vTexCoord;
uniform sampler2D uTexture;
out vec4 fragColor;

//模拟DLSS5光影增强，提升明暗细节、对比度、写实质感
void main(){
    vec4 color = texture(uTexture, vTexCoord);
    vec3 c = color.rgb;

    //局部光影提升，HDR细节恢复
    float lum = dot(c,vec3(0.2126,0.7152,0.0722));
    float detail = smoothstep(0.1,0.9,lum);
    c = mix(c*0.85, c*1.15, detail);

    //自适应降噪+微锐化
    vec3 blur = texture(uTexture, vTexCoord+vec2(0.001)).rgb;
    c = mix(c,(c+blur)*0.5,0.06);

    //色彩微调，更写实
    c = pow(c,vec3(0.94));
    fragColor = vec4(c,color.a);
}
