package elara.util.shader;

import org.lwjgl.opengl.GL20;

public final class GlowEffectShader
extends FramebufferShader {
    public static final GlowEffectShader GLOW_SHADER = new GlowEffectShader();
    private static final String FRAGMENT_SHADER = "#version 120\n\nuniform sampler2D texture;\nuniform vec2 texelSize;\n\nuniform vec3 color;\n\nuniform float radius;\nuniform float divider;\nuniform float maxSample;\n\nvoid main() {\n    vec4 centerCol = texture2D(texture, gl_TexCoord[0].xy);\n\n     if(centerCol.a != 0) {\n         gl_FragColor = vec4(centerCol.rgb, 0);\n     } else {\n\n         float alpha = 0;\n\n         for (float x = -radius; x <= radius; x ++) {\n             for (float y = -radius; y <= radius; y ++) {\n                 vec4 currentColor = texture2D(texture, gl_TexCoord[0].xy + vec2(texelSize.x * x, texelSize.y * y));\n\n                 if (currentColor.a != 0)\n                 alpha += divider > 0 ? max(0, (maxSample - distance(vec2(x, y), vec2(0))) / divider) : 1;\n             }\n         }\n         gl_FragColor = vec4(color, alpha);\n     }\n}";

    public GlowEffectShader() {
        super(FRAGMENT_SHADER);
    }

    @Override
    public void setupUniforms() {
        this.setupUniform("texture");
        this.setupUniform("texelSize");
        this.setupUniform("color");
        this.setupUniform("divider");
        this.setupUniform("radius");
        this.setupUniform("maxSample");
    }

    @Override
    public void updateUniforms() {
        GL20.glUniform1i(this.getUniform("texture"), 0);
        GL20.glUniform2f(this.getUniform("texelSize"), 1.0f / (float)mc.displayWidth * (this.radius * this.quality), 1.0f / (float)mc.displayHeight * (this.radius * this.quality));
        GL20.glUniform3f(this.getUniform("color"), this.red, this.green, this.blue);
        GL20.glUniform1f(this.getUniform("divider"), 140.0f);
        GL20.glUniform1f(this.getUniform("radius"), this.radius);
        GL20.glUniform1f(this.getUniform("maxSample"), 10.0f);
    }
}