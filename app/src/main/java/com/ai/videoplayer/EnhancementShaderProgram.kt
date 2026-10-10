package com.ai.videoplayer

import android.content.Context
import android.opengl.GLES30
import androidx.media3.effect.SingleFrameGlShaderProgram

class EnhancementShaderProgram(context: Context) :
    SingleFrameGlShaderProgram(context, /* useHighPrecisionColorComponents = */ true) {

    @Volatile
    var mode: FilterMode = FilterMode.OFF

    private var uMode = -1
    private var uTexel = -1
    private var uTime = -1
    private var texelX = 1f / 1920f
    private var texelY = 1f / 1080f

    override fun getFragmentShaderString(): String = FRAGMENT_SHADER

    override fun configureGlObjects(
        inputWidth: Int,
        inputHeight: Int,
        inputOrigin: Int,
        outputWidth: Int,
        outputHeight: Int
    ) {
        super.configureGlObjects(inputWidth, inputHeight, inputOrigin, outputWidth, outputHeight)
        uMode = glGetUniformLocation("uMode")
        uTexel = glGetUniformLocation("uTexel")
        uTime = glGetUniformLocation("uTime")
        val w = if (outputWidth > 0) outputWidth else inputWidth
        val h = if (outputHeight > 0) outputHeight else inputHeight
        if (w > 0 && h > 0) {
            texelX = 1f / w
            texelY = 1f / h
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        GLES30.glUniform1i(uMode, mode.id)
        GLES30.glUniform2f(uTexel, texelX, texelY)
        GLES30.glUniform1f(uTime, (presentationTimeUs / 1_000_000f) % 1000f)
        super.drawFrame(inputTexId, presentationTimeUs)
    }

    companion object {
        const val FRAGMENT_SHADER = """
#version 100
precision highp float;

uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform int uMode;
uniform float uTime;
varying vec2 varTexCoord;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 pxv(vec2 o) {
    return texture2D(uTexSampler, varTexCoord + o * uTexel).rgb;
}

vec3 sat(vec3 c, float s) {
    float l = luma(c);
    return mix(vec3(l), c, 1.0 + s);
}

vec3 contrastS(vec3 c, float k) {
    return mix(c, smoothstep(0.0, 1.0, c), k);
}

vec3 casSharpen(vec2 uv, float amount) {
    vec3 b = texture2D(uTexSampler, uv + vec2(0.0, -1.0) * uTexel).rgb;
    vec3 d = texture2D(uTexSampler, uv + vec2(-1.0, 0.0) * uTexel).rgb;
    vec3 e = texture2D(uTexSampler, uv).rgb;
    vec3 f = texture2D(uTexSampler, uv + vec2(1.0, 0.0) * uTexel).rgb;
    vec3 h = texture2D(uTexSampler, uv + vec2(0.0, 1.0) * uTexel).rgb;
    vec3 soft = (b + d + f + h) * 0.25;
    vec3 mn = min(e, min(min(b, d), min(f, h)));
    vec3 mx = max(e, max(max(b, d), max(f, h)));
    vec3 r = e + (e - soft) * amount;
    return clamp(r, mn, mx);
}

vec3 bloomAt(vec2 uv, float radius, float threshold, float strength) {
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 12; i++) {
        float a = (float(i) + 0.5) * 0.5235988;
        vec2 off = vec2(cos(a), sin(a)) * uTexel * radius;
        vec3 c = texture2D(uTexSampler, uv + off).rgb;
        acc += max(c - vec3(threshold), vec3(0.0));
    }
    return acc * (strength * 4.0 / 12.0);
}

vec3 localContrast(vec3 base, vec2 uv, float radius, float amount) {
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.7853982;
        vec2 off = vec2(cos(a), sin(a)) * uTexel * radius;
        acc += texture2D(uTexSampler, uv + off).rgb;
    }
    vec3 blur = acc / 8.0;
    return base + (base - blur) * amount;
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = varTexCoord;
    vec3 col = texture2D(uTexSampler, uv).rgb;

    if (uMode == 1) {
        col = casSharpen(uv, 0.55);
        col = (col - 0.012) * 1.035;
        col = contrastS(col, 0.16);
        col = sat(col, 0.08);
        col += bloomAt(uv, 14.0, 0.82, 0.10);
    } else if (uMode == 2) {
        float l0 = luma(pxv(vec2(-1.0, -1.0)));
        float l1 = luma(pxv(vec2(0.0, -1.0)));
        float l2 = luma(pxv(vec2(1.0, -1.0)));
        float l3 = luma(pxv(vec2(-1.0, 0.0)));
        float l5 = luma(pxv(vec2(1.0, 0.0)));
        float l6 = luma(pxv(vec2(-1.0, 1.0)));
        float l7 = luma(pxv(vec2(0.0, 1.0)));
        float l8 = luma(pxv(vec2(1.0, 1.0)));
        float gx = -l0 + l2 - 2.0 * l3 + 2.0 * l5 - l6 + l8;
        float gy = -l0 - 2.0 * l1 - l2 + l6 + 2.0 * l7 + l8;
        float edge = clamp(gx * gx + gy * gy, 0.0, 1.0);
        float line = smoothstep(0.012, 0.12, edge);
        float flatness = 1.0 - smoothstep(0.01, 0.08, edge);

        vec3 c0 = pxv(vec2(-1.0, -1.0));
        vec3 c1 = pxv(vec2(0.0, -1.0));
        vec3 c2 = pxv(vec2(1.0, -1.0));
        vec3 c3 = pxv(vec2(-1.0, 0.0));
        vec3 c4 = pxv(vec2(0.0, 0.0));
        vec3 c5 = pxv(vec2(1.0, 0.0));
        vec3 c6 = pxv(vec2(-1.0, 1.0));
        vec3 c7 = pxv(vec2(0.0, 1.0));
        vec3 c8 = pxv(vec2(1.0, 1.0));
        vec3 avg = (c0 + c1 + c2 + c3 + c5 + c6 + c7 + c8 + c4 * 2.0) / 10.0;

        col = casSharpen(uv, 0.45);
        col = mix(col, avg, flatness * 0.30);
        col *= 1.0 - line * 0.40;
        col = sat(col, 0.12);
    } else if (uMode == 3) {
        col = casSharpen(uv, 0.95);
        col = clamp((col - 0.01) * 1.02, 0.0, 1.0);
        col = contrastS(col, 0.10);
        col = sat(col, 0.05);
    } else if (uMode == 4) {
        col = casSharpen(uv, 0.35);
        col = localContrast(col, uv, 28.0, 0.30);
        vec3 hi = smoothstep(0.55, 1.0, col);
        col += hi * 0.05;
        col += bloomAt(uv, 30.0, 0.62, 0.22);
        col = max(col - 0.01, 0.0) * 1.02;
        col = sat(col, 0.06);
    } else if (uMode == 5) {
        col = contrastS(col, 0.28);
        float l = luma(col);
        col += vec3(0.0, 0.035, 0.06) * (1.0 - smoothstep(0.0, 0.45, l));
        col += vec3(0.06, 0.03, 0.0) * smoothstep(0.45, 1.0, l);
        vec2 dd = uv - 0.5;
        float vig = clamp(1.0 - dot(dd, dd) * 1.1, 0.55, 1.0);
        col *= vig;
        vec2 res = vec2(1.0) / uTexel;
        float g = hash21(uv * res + vec2(uTime * 23.0, uTime * 17.0)) - 0.5;
        col += g * 0.035;
        col = sat(col, -0.05);
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}
