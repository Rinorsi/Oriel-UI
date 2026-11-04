#version 330
#ifndef ORIEL_26_2
#extension GL_ARB_separate_shader_objects : require
#endif

// See layer.vsh. Unlike vanilla's position_tex_color, transparent pixels are not discarded: a mask or a
// destination-in blend has to write them.

#ifdef ORIEL_26_2
#moj_import <minecraft:dynamictransforms.glsl>
#define AT(n)
#else
#include <minecraft:dynamictransforms.glsl>
#define AT(n) layout(location = n)
#endif

uniform sampler2D Sampler0;

AT(0) in vec2 texCoord0;
AT(1) in vec4 vertexColor;

AT(0) out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
