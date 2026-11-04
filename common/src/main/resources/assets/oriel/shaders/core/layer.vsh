#version 330

// Oriel's offscreen layers (Layers, LayerRenderer): a textured quad, as vanilla's position_tex_color.
// ORIEL_26_2 is defined by the pipelines of 26.1 and 26.2, which import with #moj_import and match by name.

#ifdef ORIEL_26_2
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#define AT(n)
#else
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#define AT(n) layout(location = n)
#endif

AT(0) in vec3 Position;
AT(1) in vec2 UV0;
AT(2) in vec4 Color;

AT(0) out vec2 texCoord0;
AT(1) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
