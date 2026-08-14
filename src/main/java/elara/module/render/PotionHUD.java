package elara.module.render;

import elara.event.EventTarget;
import elara.events.Render2DEvent;
import elara.module.Module;
import elara.property.properties.*;
import elara.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class PotionHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat decimalFormat = new DecimalFormat("0.0");

    private static final Map<String, String> POTION_NAME_MAP = new HashMap<>();

    static {
        POTION_NAME_MAP.put("potion.moveSpeed", "Speed");
        POTION_NAME_MAP.put("potion.moveSlowdown", "Slowness");
        POTION_NAME_MAP.put("potion.digSpeed", "Haste");
        POTION_NAME_MAP.put("potion.digSlowDown", "Mining Fatigue");
        POTION_NAME_MAP.put("potion.damageBoost", "Strength");
        POTION_NAME_MAP.put("potion.heal", "Instant Health");
        POTION_NAME_MAP.put("potion.harm", "Instant Damage");
        POTION_NAME_MAP.put("potion.jump", "Jump Boost");
        POTION_NAME_MAP.put("potion.confusion", "Nausea");
        POTION_NAME_MAP.put("potion.regeneration", "Regeneration");
        POTION_NAME_MAP.put("potion.resistance", "Resistance");
        POTION_NAME_MAP.put("potion.fireResistance", "Fire Resistance");
        POTION_NAME_MAP.put("potion.waterBreathing", "Water Breathing");
        POTION_NAME_MAP.put("potion.invisibility", "Invisibility");
        POTION_NAME_MAP.put("potion.blindness", "Blindness");
        POTION_NAME_MAP.put("potion.nightVision", "Night Vision");
        POTION_NAME_MAP.put("potion.hunger", "Hunger");
        POTION_NAME_MAP.put("potion.weakness", "Weakness");
        POTION_NAME_MAP.put("potion.poison", "Poison");
        POTION_NAME_MAP.put("potion.wither", "Wither");
        POTION_NAME_MAP.put("potion.healthBoost", "Health Boost");
        POTION_NAME_MAP.put("potion.absorption", "Absorption");
        POTION_NAME_MAP.put("potion.saturation", "Saturation");
    }

    public final ModeProperty posX = new ModeProperty("PositionX", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("PositionY", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("OffsetX", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("OffsetY", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 2.0F);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty duration = new BooleanProperty("Duration", true);
    public final BooleanProperty amplifier = new BooleanProperty("Amplifier", false);
    public final ModeProperty sortMode = new ModeProperty("SortMode", 0, new String[]{"LENGTH", "STRENGTH", "NONE"});
    public final BooleanProperty reverse = new BooleanProperty("Reverse", false);

    public PotionHUD() {
        super("PotionHUD", true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        if (mc.thePlayer == null) return;
        if (mc.thePlayer.getActivePotionEffects().isEmpty()) return;

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale.getValue(), scale.getValue(), 1.0F);

        java.util.List<PotionEffect> effects = new java.util.ArrayList<>(mc.thePlayer.getActivePotionEffects());
        sortEffects(effects);

        if (reverse.getValue()) {
            java.util.Collections.reverse(effects);
        }

        int iconSize = 18;
        int iconGap = 6;
        int paddingX = 4;
        int paddingY = 2;
        int entryHeight = 20;

        int x = offsetX.getValue();
        int y = offsetY.getValue();

        if (posX.getValue() == 1) {
            int maxWidth = 0;
            for (PotionEffect effect : effects) {
                String name = getPotionName(effect);
                if (amplifier.getValue() && effect.getAmplifier() > 0) {
                    name += " " + (effect.getAmplifier() + 1);
                }
                int nameWidth = mc.fontRendererObj.getStringWidth(name);
                int width = paddingX * 2 + iconSize + iconGap + nameWidth;
                if (duration.getValue()) {
                    width += mc.fontRendererObj.getStringWidth(formatDuration(effect.getDuration())) + 4;
                }
                if (width > maxWidth) maxWidth = width;
            }
            x = (int) (scaledResolution.getScaledWidth() / scale.getValue() - maxWidth - x);
        }

        if (posY.getValue() == 1) {
            y = (int) (scaledResolution.getScaledHeight() / scale.getValue() - effects.size() * entryHeight - paddingY * 2 - y);
        }

        for (int i = 0; i < effects.size(); i++) {
            PotionEffect effect = effects.get(i);
            int effectY = y + paddingY + i * entryHeight;

            String name = getPotionName(effect);
            if (amplifier.getValue() && effect.getAmplifier() > 0) {
                name += " " + (effect.getAmplifier() + 1);
            }

            int nameWidth = mc.fontRendererObj.getStringWidth(name);
            int totalWidth = paddingX * 2 + iconSize + iconGap + nameWidth;
            if (duration.getValue()) {
                totalWidth += mc.fontRendererObj.getStringWidth(formatDuration(effect.getDuration())) + 4;
            }

            int iconX = x + paddingX;
            int iconY = effectY + (entryHeight - iconSize) / 2;
            drawPotionIcon(effect, iconX, iconY);

            int textX = iconX + iconSize + iconGap;
            int textY = effectY + (entryHeight - mc.fontRendererObj.FONT_HEIGHT) / 2;

            mc.fontRendererObj.drawString(name, textX, textY, -1, shadow.getValue());

            if (duration.getValue()) {
                String durationStr = formatDuration(effect.getDuration());
                mc.fontRendererObj.drawString(durationStr, textX + nameWidth + 4, textY, -8355712, shadow.getValue());
            }
        }

        GlStateManager.popMatrix();
    }

    private String getPotionName(PotionEffect effect) {
        Potion potion = Potion.potionTypes[effect.getPotionID()];
        if (potion == null) return "Unknown";
        String name = potion.getName();
        return POTION_NAME_MAP.getOrDefault(name, name);
    }

    private void drawPotionIcon(PotionEffect effect, int x, int y) {
        RenderUtil.renderPotionEffect(effect, x, y);
    }

    private void sortEffects(java.util.List<PotionEffect> effects) {
        switch (sortMode.getValue()) {
            case 0:
                effects.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
                break;
            case 1:
                effects.sort((a, b) -> Integer.compare(b.getAmplifier(), a.getAmplifier()));
                break;
        }
    }

    private String formatDuration(int ticks) {
        int seconds = ticks / 20;
        if (seconds < 60) {
            return decimalFormat.format(seconds / 1.0) + "s";
        }
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return minutes + ":" + (remainingSeconds < 10 ? "0" : "") + remainingSeconds;
    }
}