package elara.module.render;

import elara.Elara;
import elara.enums.BlinkModules;
import elara.enums.ChatColors;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.Render2DEvent;
import elara.events.TickEvent;
import elara.mixin.IAccessorGuiChat;
import elara.module.Module;
import elara.util.ColorUtil;
import elara.util.RenderUtil;
import elara.util.shader.BlurUtils;
import elara.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private List<Module> activeModules = new ArrayList<>();
    private ScaledResolution cachedResolution = null;
    private long lastResolutionUpdate = 0L;
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 25);
    public static final int BLOOM_MASK_ALPHA = 210;
    public static final int BLUR_MASK_ALPHA = 110;
    private static final float BLOOM_RADIUS = 2.0f;
    private static final float BLUR_RADIUS = 3.0f;
    private static final int BLOOM_PASSES = 3;
    private static final int BLUR_PASSES = 2;
    public final BooleanProperty bloom = new BooleanProperty("bloom", true);
    public final ModeProperty bloomColor = new ModeProperty("bloom-color", 0, new String[]{"DEFAULT", "HUD"}, () -> bloom.getValue());
    public final ModeProperty bloomScaleMode = new ModeProperty("bloom-scale-mode", 1, new String[]{"FOLLOW", "FIXED_SCREEN"}, () -> bloom.getValue());
    // Bloom 放大不跟随：FIXED_SCREEN 下 bloom 半径/扩散以屏幕坐标为准，不受 HUD.scale 放大影响
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = mc.fontRendererObj.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + mc.fontRendererObj.getStringWidth(str);
            }
        }
        return width;
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", true, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            this.activeModules = Elara.moduleManager.modules.values().stream().filter(module -> module.isEnabled() && !module.isHidden()).sorted(Comparator.comparingInt(this::getModuleWidth).reversed()).collect(Collectors.<Module>toList());
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Elara.commandManager != null && Elara.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            // Cache resolution to avoid repeated ScaledResolution creation
            long now = System.currentTimeMillis();
            if (cachedResolution == null || now - lastResolutionUpdate > 100L) {
                cachedResolution = new ScaledResolution(mc);
                lastResolutionUpdate = now;
            }
            
            float height = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
            float x = (float) this.offsetX.getValue()
                    + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * this.scale.getValue();
            float y = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
            if (this.posX.getValue() == 1) {
                x = (float) cachedResolution.getScaledWidth() - x;
            }
            if (this.posY.getValue() == 1) {
                y = (float) cachedResolution.getScaledHeight() - y - height * this.scale.getValue();
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

            float invScale = 1.0f / this.scale.getValue();
            boolean bloomFixed = this.bloomScaleMode.getValue() == 1; // FIXED_SCREEN：放大不跟随
            // ---- Emit Bloom/Blur per-module masks (cropped to each line's actual bounds)
            // ---- Mask alpha is fixed, decoupled from the `background` property
            // ---- FIXED_SCREEN 模式下按屏幕像素尺寸计算 bloom，不跟随 HUD.scale 放大
            boolean hasAny = !this.activeModules.isEmpty();
            if (hasAny) {
                float lineH = height + (this.shadow.getValue() ? 1.0F : 0.0F);
                // FOLLOW 模式：坐标用 scaled-space（除以 scale），使得模糊半径随 HUD 整体缩放一起放大
                // FIXED_SCREEN 模式：坐标用屏幕空间（x/y，乘 scale 得到 mask 的宽高），bloom 扩散半径以屏幕像素为准
                float maskOriginX;
                float maskOriginY;
                float maskWScale;  // totalWidth 的放大倍数
                float maskHScale;  // lineH 的放大倍数
                float maskYStep;   // 行间距
                float pad;         // 每边外扩像素
                if (bloomFixed) {
                    maskOriginX = x;
                    maskOriginY = y;
                    maskWScale = this.scale.getValue();
                    maskHScale = this.scale.getValue();
                    maskYStep = lineH * this.scale.getValue();
                    pad = 1.0F * this.scale.getValue();
                } else {
                    maskOriginX = x * invScale;
                    maskOriginY = y * invScale;
                    maskWScale = 1.0F;
                    maskHScale = 1.0F;
                    maskYStep = lineH * invScale;
                    pad = 1.0F;
                }
                int blurMask = new Color(0, 0, 0, BLUR_MASK_ALPHA).getRGB();
                if (this.bloom.getValue()) {
                    long bloomTime = System.currentTimeMillis();
                    int defaultBloomMask = new Color(0, 0, 0, BLOOM_MASK_ALPHA).getRGB();
                    BlurUtils.prepareBloom();
                    {
                        float curY = maskOriginY;
                        long fakeOffset = 0L;
                        for (Module module : this.activeModules) {
                            String moduleName = this.getModuleName(module);
                            String[] moduleSuffix = this.getModuleSuffix(module);
                            float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix)
                                    - (this.shadow.getValue() ? 0 : 1)) * maskWScale;
                            float lineHeight = height * maskHScale;
                            float leftX;
                            float rightX;
                            if (this.posX.getValue() == 0) {
                                leftX = maskOriginX - pad;
                                rightX = maskOriginX + pad + totalWidth;
                            } else {
                                leftX = maskOriginX - pad - totalWidth;
                                rightX = maskOriginX + pad;
                            }
                            float topY;
                            float botY;
                            float topPad = (fakeOffset == 0L) ? pad : 0.0F;
                            float botPad = (this.shadow.getValue() ? pad : 0.0F);
                            if (this.posY.getValue() == 0) {
                                topY = curY - topPad;
                                botY = curY + lineHeight + botPad;
                            } else {
                                topY = curY - botPad;
                                botY = curY + lineHeight + topPad;
                            }
                            int bloomMask;
                            if (this.bloomColor.getValue() == 1) {
                                Color bloomCol = this.getColor(bloomTime, fakeOffset);
                                bloomMask = new Color(bloomCol.getRed(), bloomCol.getGreen(), bloomCol.getBlue(), BLOOM_MASK_ALPHA).getRGB();
                            } else {
                                bloomMask = defaultBloomMask;
                            }
                            RenderUtil.drawRect(leftX, topY, rightX, botY, bloomMask);
                            curY += maskYStep * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
                            fakeOffset++;
                        }
                    }
                    BlurUtils.bloomEnd(BLOOM_PASSES, BLOOM_RADIUS);
                }

                BlurUtils.prepareBlur();
                {
                    float curY = maskOriginY;
                    long fakeOffset = 0L;
                    for (Module module : this.activeModules) {
                        String moduleName = this.getModuleName(module);
                        String[] moduleSuffix = this.getModuleSuffix(module);
                        float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix)
                                - (this.shadow.getValue() ? 0 : 1)) * maskWScale;
                        float lineHeight = height * maskHScale;
                        float leftX;
                        float rightX;
                        if (this.posX.getValue() == 0) {
                            leftX = maskOriginX - pad;
                            rightX = maskOriginX + pad + totalWidth;
                        } else {
                            leftX = maskOriginX - pad - totalWidth;
                            rightX = maskOriginX + pad;
                        }
                        float topY;
                        float botY;
                        float topPad = (fakeOffset == 0L) ? pad : 0.0F;
                        float botPad = (this.shadow.getValue() ? pad : 0.0F);
                        if (this.posY.getValue() == 0) {
                            topY = curY - topPad;
                            botY = curY + lineHeight + botPad;
                        } else {
                            topY = curY - botPad;
                            botY = curY + lineHeight + topPad;
                        }
                        RenderUtil.drawRect(leftX, topY, rightX, botY, blurMask);
                        curY += maskYStep * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
                        fakeOffset++;
                    }
                }
                BlurUtils.blurEnd(BLUR_PASSES, BLUR_RADIUS);
            }

            long l = System.currentTimeMillis();
            long offset = 0L;
            for (Module module : this.activeModules) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1));
                int color = this.getColor(l, offset).getRGB();
                RenderUtil.enableRenderState();
                if (this.background.getValue() > 0) {
                    RenderUtil.drawRect(
                            x / this.scale.getValue() - 1.0F - (this.posX.getValue() == 0 ? 0.0F : totalWidth),
                            y / this.scale.getValue() - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F)),
                            x / this.scale.getValue() + 1.0F + (this.posX.getValue() == 0 ? totalWidth : 0.0F),
                            y / this.scale.getValue() + height + (this.posY.getValue() == 0 ? (this.shadow.getValue() ? 1.0F : 0.0F) : (offset == 0L ? 1.0F : 0.0F)),
                            new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F).getRGB()
                    );
                }
                if (this.showBar.getValue()) {
                    if (this.shadow.getValue()) {
                        RenderUtil.drawRect(
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -3.0F : 1.0F),
                                y / this.scale.getValue() - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 1.0F),
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                                y / this.scale.getValue() + height + (this.posY.getValue() == 0 ? 1.0F : (offset == 0L ? 1.0F : 0.0F)),
                                color
                        );
                        RenderUtil.drawRect(
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                                y / this.scale.getValue() - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 1.0F),
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -1.0F : 3.0F),
                                y / this.scale.getValue() + height + (this.posY.getValue() == 0 ? 1.0F : (offset == 0L ? 1.0F : 0.0F)),
                                (color & 16579836) >> 2 | color & 0xFF000000
                        );
                    } else {
                        RenderUtil.drawRect(
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -2.0F : 1.0F),
                                y / this.scale.getValue() - (this.posY.getValue() == 0 ? (offset == 0L ? 1.0F : 0.0F) : 0.0F),
                                x / this.scale.getValue() + (this.posX.getValue() == 0 ? -1.0F : 2.0F),
                                y / this.scale.getValue() + height + (this.posY.getValue() == 0 ? 0.0F : (offset == 0L ? 1.0F : 0.0F)),
                                color
                        );
                    }
                }
                RenderUtil.disableRenderState();
                GlStateManager.disableDepth();
                if (this.shadow.getValue()) {
                    mc.fontRendererObj
                            .drawStringWithShadow(moduleName, x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F), y / this.scale.getValue(), color);
                } else {
                    mc.fontRendererObj
                            .drawString(
                                    moduleName,
                                    x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F),
                                    y / this.scale.getValue() + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                                    color,
                                    false
                            );
                }
                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    float width = (float) mc.fontRendererObj.getStringWidth(moduleName) + 3.0F;
                    for (String string : moduleSuffix) {
                        if (this.shadow.getValue()) {
                            mc.fontRendererObj
                                    .drawStringWithShadow(
                                            string,
                                            x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F) + width,
                                            y / this.scale.getValue(),
                                            ChatColors.GRAY.toAwtColor()
                                    );
                        } else {
                            mc.fontRendererObj
                                    .drawString(
                                            string,
                                            x / this.scale.getValue() - (this.posX.getValue() == 1 ? totalWidth : 0.0F) + width,
                                            y / this.scale.getValue() + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                                            ChatColors.GRAY.toAwtColor(),
                                            false
                                    );
                        }
                        width += (float) mc.fontRendererObj.getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                    }
                }
                y += (height + (this.shadow.getValue() ? 1.0F : 0.0F)) * this.scale.getValue() * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
                offset++;
            }
            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = Elara.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = Elara.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        mc.fontRendererObj
                                .drawString(
                                        String.valueOf(movementPacketSize),
                                        (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                                                - (float) mc.fontRendererObj.getStringWidth(String.valueOf(movementPacketSize)) / 2.0F,
                                        (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                                        this.getColor(l, offset).getRGB() & 16777215 | -1090519040,
                                        this.shadow.getValue()
                                );
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }
}
