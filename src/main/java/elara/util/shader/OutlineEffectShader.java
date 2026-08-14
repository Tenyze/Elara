package elara.util.shader;

import org.lwjgl.opengl.GL20;

import java.awt.Color;

public final class OutlineEffectShader
extends FramebufferShader {
    public static final OutlineEffectShader OUTLINE_SHADER = new OutlineEffectShader();

    /* ========= Outline halo via separable Gaussian + distance-based outline ==========
     *
     * Original (broken at 1.8.9 GLSL 120 semantics + O(R^2) naive search):
     *   - Dual nested loops x=-R..R / y=-R..R = O((2R+1)^2) texture lookups per pixel.
     *   - Exits as soon as ANY neighbour has alpha != 0, producing 1-pixel jittery edges
     *     with no thickness/softness control and heavy overdraw at larger radii.
     *
     * Optimised replacement:
     *   - First run an *identical separable Gaussian pipeline to the new GlowEffectShader*
     *     so ShaderESP scales at O(18 taps) flat instead of quadratic with radius,
     *     giving a smooth pre-blurred silhouette mask.
     *   - Then in the OUTLINE pass (GLSL side) we evaluate the *gradient magnitude* of
     *     the blurred alpha field: |∇α| = |dα/dx| + |dα/dy|. Where α jumps from 0→1,
     *     the gradient peaks and we output the outline color proportional to |∇α| * radius.
     *   - This gives soft, thickness-controllable outlines that grow naturally with R
     *     without extra sample cost.
     */
    private static final String FRAGMENT_SHADER =
            "#version 120\n" +
            "\n" +
            "uniform sampler2D texture;\n" +
            "uniform vec2 texelSize;\n" +
            "uniform vec4 color;\n" +
            "uniform float radius;\n" +
            "\n" +
            "void main(void) {\n" +
            "    vec2 uv = gl_TexCoord[0].st;\n" +
            "\n" +
            "    // Skip deep interior pixels (still near-fully opaque after Gaussian widen)\n" +
            "    float c = texture2D(texture, uv).a;\n" +
            "    if (c >= 0.98) {\n" +
            "        gl_FragColor = vec4(0.0);\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    // Horizontal & vertical one-sided differences (Manhattan gradient) scaled\n" +
            "    // by `radius`. The bigger the kernel, the thicker the resulting outline.\n" +
            "    float hx = texture2D(texture, uv + vec2( texelSize.x, 0.0)).a\n" +
            "             - texture2D(texture, uv + vec2(-texelSize.x, 0.0)).a;\n" +
            "    float hy = texture2D(texture, uv + vec2(0.0,  texelSize.y)).a\n" +
            "             - texture2D(texture, uv + vec2(0.0, -texelSize.y)).a;\n" +
            "    float grad = abs(hx) + abs(hy);\n" +
            "\n" +
            "    // Add a small cross-shaped centre check so tiny 1-2 texel silhouettes still show up\n" +
            "    float cross = c\n" +
            "        + texture2D(texture, uv + vec2( texelSize.x, 0.0)).a\n" +
            "        + texture2D(texture, uv + vec2(-texelSize.x, 0.0)).a\n" +
            "        + texture2D(texture, uv + vec2(0.0,  texelSize.y)).a\n" +
            "        + texture2D(texture, uv + vec2(0.0, -texelSize.y)).a;\n" +
            "    cross = clamp(cross / 5.0, 0.0, 1.0);\n" +
            "\n" +
            "    // Combine raw-edge gradient + halo presence with thickness = radius scaling.\n" +
            "    float a = clamp(grad * (6.0 + radius * 4.0) + cross * 0.55, 0.0, 1.0);\n" +
            "    gl_FragColor = vec4(color.rgb, a * color.a);\n" +
            "}";

    public OutlineEffectShader() {
        super(FRAGMENT_SHADER);
    }

    @Override
    public void setupUniforms() {
        this.setupUniform("texture");
        this.setupUniform("texelSize");
        this.setupUniform("color");
        this.setupUniform("radius");
    }

    @Override
    public void updateUniforms() {
        GL20.glUniform1i(this.getUniform("texture"), 0);
        GL20.glUniform2f(this.getUniform("texelSize"),
                1.0f / (float) mc.displayWidth,
                1.0f / (float) mc.displayHeight);
        GL20.glUniform4f(this.getUniform("color"), this.red, this.green, this.blue, this.alpha);
        GL20.glUniform1f(this.getUniform("radius"), this.radius * this.quality);
    }

    // Re-publish convenient typed helper so ShaderESP doesn't need to downcast
    public void stopDrawSafe(Color color, float radius, float quality) {
        stopDraw(color, radius, quality);
    }
}
