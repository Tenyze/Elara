package elara.util.shader;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public abstract class FramebufferShader {
    protected static final Minecraft mc = Minecraft.getMinecraft();
    private static final String VERTEX_SHADER = "#version 120\n\nvoid main(void) {\n    gl_TexCoord[0] = gl_MultiTexCoord0;\n    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n}";

    private int programId;
    private Map<String, Integer> uniformsMap;
    private static Framebuffer framebuffer;
    private boolean entityShadows;

    protected float red, green, blue, alpha = 1.0f;
    protected float radius = 2.0f;
    protected float quality = 1.0f;

    public FramebufferShader(String fragmentShader) {
        this.createProgram(fragmentShader);
    }

    private int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            System.err.println("[FramebufferShader] Failed to create shader object (type=" + type + ")");
            return -1;
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        int compiled = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (compiled == 0) {
            String log = GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
            System.err.println("[FramebufferShader] Shader compile error (type=" + type + "): " + log);
            GL20.glDeleteShader(shader);
            return -1;
        }
        return shader;
    }

    private void createProgram(String fragmentSource) {
        this.programId = GL20.glCreateProgram();
        if (this.programId == 0) {
            System.err.println("[FramebufferShader] Failed to create GL program");
            this.programId = -1;
            return;
        }
        int vs = this.compileShader(VERTEX_SHADER, GL20.GL_VERTEX_SHADER);
        int fs = this.compileShader(fragmentSource, GL20.GL_FRAGMENT_SHADER);
        if (vs == -1 || fs == -1) {
            if (vs != -1) GL20.glDeleteShader(vs);
            if (fs != -1) GL20.glDeleteShader(fs);
            GL20.glDeleteProgram(this.programId);
            this.programId = -1;
            return;
        }
        GL20.glAttachShader(this.programId, vs);
        GL20.glAttachShader(this.programId, fs);
        GL20.glLinkProgram(this.programId);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        int linked = GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS);
        if (linked == 0) {
            String log = GL20.glGetProgramInfoLog(this.programId, GL20.glGetProgrami(this.programId, GL20.GL_INFO_LOG_LENGTH));
            System.err.println("[FramebufferShader] Program link error: " + log);
            GL20.glDeleteProgram(this.programId);
            this.programId = -1;
            return;
        }
        this.uniformsMap = new HashMap<String, Integer>();
        this.setupUniforms();
    }

    public void startShader() {
        if (this.programId == -1) return;
        GL11.glPushMatrix();
        GL20.glUseProgram(this.programId);
        this.updateUniforms();
    }

    public void stopShader() {
        GL20.glUseProgram(0);
        GL11.glPopMatrix();
    }

    public abstract void setupUniforms();

    public abstract void updateUniforms();

    public void setupUniform(String uniformName) {
        if (this.programId == -1) return;
        this.uniformsMap.put(uniformName, GL20.glGetUniformLocation(this.programId, uniformName));
    }

    public int getUniform(String uniformName) {
        Integer v = this.uniformsMap.get(uniformName);
        return v == null ? -1 : v;
    }

    public void startDraw(float partialTicks) {
        GlStateManager.enableAlpha();
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        framebuffer = this.setupFrameBuffer(framebuffer);
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);
        this.entityShadows = FramebufferShader.mc.gameSettings.entityShadows;
        FramebufferShader.mc.gameSettings.entityShadows = false;
        try {
            Method setupCameraTransformMethod = Class.forName("net.minecraft.client.renderer.EntityRenderer").getDeclaredMethod("func_78479_a", float.class, int.class);
            setupCameraTransformMethod.setAccessible(true);
            setupCameraTransformMethod.invoke(FramebufferShader.mc.entityRenderer, partialTicks, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopDraw(Color color, float radius, float quality) {
        FramebufferShader.mc.gameSettings.entityShadows = this.entityShadows;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        FramebufferShader.mc.getFramebuffer().bindFramebuffer(true);
        this.red = (float)color.getRed() / 255.0f;
        this.green = (float)color.getGreen() / 255.0f;
        this.blue = (float)color.getBlue() / 255.0f;
        this.alpha = (float)color.getAlpha() / 255.0f;
        this.radius = radius;
        this.quality = quality;
        FramebufferShader.mc.entityRenderer.disableLightmap();
        RenderHelper.disableStandardItemLighting();
        this.startShader();
        FramebufferShader.mc.entityRenderer.setupOverlayRendering();
        this.drawFramebuffer(framebuffer);
        this.stopShader();
        FramebufferShader.mc.entityRenderer.disableLightmap();
        GlStateManager.popMatrix();
        GlStateManager.popAttrib();
    }

    public Framebuffer setupFrameBuffer(Framebuffer frameBuffer) {
        if (frameBuffer != null) {
            try {
                frameBuffer.deleteFramebuffer();
            } catch (Throwable ignored) {
            }
        }
        frameBuffer = new Framebuffer(FramebufferShader.mc.displayWidth, FramebufferShader.mc.displayHeight, true);
        return frameBuffer;
    }

    public void drawFramebuffer(Framebuffer framebuffer) {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0.0, 1.0);
        GL11.glVertex2d(0.0, 0.0);
        GL11.glTexCoord2d(0.0, 0.0);
        GL11.glVertex2d(0.0, scaledResolution.getScaledHeight());
        GL11.glTexCoord2d(1.0, 0.0);
        GL11.glVertex2d(scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight());
        GL11.glTexCoord2d(1.0, 1.0);
        GL11.glVertex2d(scaledResolution.getScaledWidth(), 0.0);
        GL11.glEnd();
        GL20.glUseProgram(0);
    }
}