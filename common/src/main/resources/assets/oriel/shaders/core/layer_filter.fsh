#version 330
#ifndef ORIEL_26_2
#extension GL_ARB_separate_shader_objects : require
#endif

// See layer_filter.vsh. The layers are premultiplied: colour filters work on the colour divided by its alpha, as
// Filter Effects 1 defines them (the same matrices as Filter.matrix); blurs are Gaussian, one direction a pass;
// TINT is a drop shadow's first step, the vertex colour where the layer is opaque.

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
AT(2) flat in float amount;

AT(0) out vec4 fragColor;

vec3 rgb3(vec3 c, vec3 r, vec3 g, vec3 b) {
    return vec3(dot(r, c), dot(g, c), dot(b, c));
}

#if defined(BLUR_X) || defined(BLUR_Y)
vec4 blur() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
#ifdef BLUR_X
    vec2 step = vec2(texel.x, 0.0);
#else
    vec2 step = vec2(0.0, texel.y);
#endif
    float sigma = max(amount, 0.01);
    int radius = min(int(ceil(sigma * 3.0)), 96);
    vec4 sum = texture(Sampler0, texCoord0);
    float total = 1.0;
    for (int i = 1; i <= radius; i++) {
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        sum += (texture(Sampler0, texCoord0 + step * float(i)) + texture(Sampler0, texCoord0 - step * float(i))) * w;
        total += 2.0 * w;
    }
    return sum / total;
}
#endif

void main() {
    vec4 p = texture(Sampler0, texCoord0);
#if defined(BLUR_X) || defined(BLUR_Y)
    fragColor = blur();
#elif defined(TINT)
    fragColor = vertexColor * p.a;
#else
    float a = p.a;
    vec3 c = a > 0.0 ? p.rgb / a : vec3(0.0);
    float k = amount;
#if defined(BRIGHTNESS)
    c *= k;
#elif defined(CONTRAST)
    c = c * k + (0.5 - 0.5 * k);
#elif defined(INVERT)
    float i = clamp(k, 0.0, 1.0);
    c = c * (1.0 - 2.0 * i) + i;
#elif defined(OPACITY)
    a *= clamp(k, 0.0, 1.0);
#elif defined(GRAYSCALE)
    float g = 1.0 - clamp(k, 0.0, 1.0);
    c = rgb3(c, vec3(0.2126 + 0.7874 * g, 0.7152 - 0.7152 * g, 0.0722 - 0.0722 * g),
               vec3(0.2126 - 0.2126 * g, 0.7152 + 0.2848 * g, 0.0722 - 0.0722 * g),
               vec3(0.2126 - 0.2126 * g, 0.7152 - 0.7152 * g, 0.0722 + 0.9278 * g));
#elif defined(SEPIA)
    float s = 1.0 - clamp(k, 0.0, 1.0);
    c = rgb3(c, vec3(0.393 + 0.607 * s, 0.769 - 0.769 * s, 0.189 - 0.189 * s),
               vec3(0.349 - 0.349 * s, 0.686 + 0.314 * s, 0.168 - 0.168 * s),
               vec3(0.272 - 0.272 * s, 0.534 - 0.534 * s, 0.131 + 0.869 * s));
#elif defined(SATURATE)
    c = rgb3(c, vec3(0.213 + 0.787 * k, 0.715 - 0.715 * k, 0.072 - 0.072 * k),
               vec3(0.213 - 0.213 * k, 0.715 + 0.285 * k, 0.072 - 0.072 * k),
               vec3(0.213 - 0.213 * k, 0.715 - 0.715 * k, 0.072 + 0.928 * k));
#elif defined(HUE_ROTATE)
    float r = radians(k), co = cos(r), si = sin(r);
    c = rgb3(c, vec3(0.213 + co * 0.787 - si * 0.213, 0.715 - co * 0.715 - si * 0.715, 0.072 - co * 0.072 + si * 0.928),
               vec3(0.213 - co * 0.213 + si * 0.143, 0.715 + co * 0.285 + si * 0.140, 0.072 - co * 0.072 - si * 0.283),
               vec3(0.213 - co * 0.213 - si * 0.787, 0.715 - co * 0.715 + si * 0.715, 0.072 + co * 0.928 + si * 0.072));
#endif
    c = clamp(c, 0.0, 1.0);
    fragColor = vec4(c * a, a);
#endif
}
