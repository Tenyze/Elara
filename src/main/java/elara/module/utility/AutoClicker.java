package elara.module.utility;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.event.types.Priority;
import elara.events.LeftClickMouseEvent;
import elara.events.TickEvent;
import elara.module.Module;
import elara.module.combat.KillAura;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import elara.util.ItemUtil;
import elara.util.KeyBindUtil;
import elara.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty minCPS = new IntProperty("Min CPS", 14, 1, 20);
    public final IntProperty maxCPS = new IntProperty("Max CPS", 18, 1, 20);
    public final BooleanProperty weaponsOnly = new BooleanProperty("Weapons Only", true);
    public final BooleanProperty allowTools = new BooleanProperty("Allow Tools", false, weaponsOnly::getValue);
    public final BooleanProperty breakBlocks = new BooleanProperty("Break Blocks", true);

    private int atkTickCd = 0;

    public AutoClicker() {
        super("AutoClicker", false);
    }

    private boolean canClick() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (mc.currentScreen != null) return false;

        if (!weaponsOnly.getValue()) {
            // 非武器限定：直接允许点击（空手/任意物品均可连点）
            if (!breakBlocks.getValue() && mc.objectMouseOver != null &&
                    mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                return false;
            }
            return true;
        }

        // 武器限定模式：持剑（附魔判定）或允许工具时持工具
        if (ItemUtil.hasRawUnbreakingEnchant() ||
                (allowTools.getValue() && ItemUtil.isHoldingTool())) {
            if (!breakBlocks.getValue() && mc.objectMouseOver != null &&
                    mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                return false;
            }
            return true;
        }
        return false;
    }

    private Entity getTarget() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            return mc.objectMouseOver.entityHit;
        }
        KillAura ka = (KillAura) Elara.moduleManager.getModule(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
            return ka.getTarget();
        }
        return null;
    }

    private boolean isValidTarget(Entity e) {
        if (e == null || e == mc.thePlayer) return false;
        if (!(e instanceof EntityLivingBase)) return false;
        EntityLivingBase living = (EntityLivingBase) e;
        return living.deathTime <= 0;
    }

    /**
     * 根据 [minCPS, maxCPS] 范围随机生成下一次点击的 tick 间隔。
     * 1.8.9 下 CPS = 20 / ticks（每秒20tick），带轻微随机抖动。
     */
    private int nextCdTicksFromCps() {
        int min = Math.min(minCPS.getValue(), maxCPS.getValue());
        int max = Math.max(minCPS.getValue(), maxCPS.getValue());
        if (max < 1) max = 1;
        if (min < 1) min = 1;
        // 在 [min, max] 中随机一个 CPS 值，转成 ticks
        float cps = RandomUtil.nextFloat((float) min, (float) max);
        if (cps <= 0.1f) cps = 0.1f;
        int ticks = (int) Math.round(20.0 / cps);
        // 加入 ±1 tick 的额外抖动，避免节奏过于固定
        ticks += RandomUtil.nextInt(-1, 2);
        return Math.max(0, ticks);
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;

        if (!canClick()) {
            atkTickCd = 0;
            return;
        }

        boolean keyDown = mc.gameSettings.keyBindAttack.isKeyDown();
        if (!keyDown) {
            atkTickCd = 0;
            return;
        }

        if (mc.thePlayer.isUsingItem()) return;
        if (mc.thePlayer.isBlocking()) return;

        if (atkTickCd > 0) {
            atkTickCd--;
            return;
        }

        Entity target = getTarget();
        boolean clicked = false;

        if (target != null && isValidTarget(target)) {
            mc.thePlayer.swingItem();
            mc.playerController.attackEntity(mc.thePlayer, target);
            clicked = true;
        } else if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity hit = mc.objectMouseOver.entityHit;
            if (isValidTarget(hit)) {
                mc.thePlayer.swingItem();
                mc.playerController.attackEntity(mc.thePlayer, hit);
                clicked = true;
            }
        }

        if (!clicked) {
            // 空点（无有效目标但左键按住）：同样 swing，保证连点手感
            if (breakBlocks.getValue() && mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                // 打方块场景交给 mc 原生处理，不主动 swing（避免冲突）
            } else {
                mc.thePlayer.swingItem();
            }
        }

        atkTickCd = nextCdTicksFromCps();
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (isEnabled() && mc.gameSettings.keyBindAttack.isKeyDown()) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        atkTickCd = 0;
    }

    @Override
    public void onDisabled() {
        atkTickCd = 0;
        if (mc.thePlayer != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
        }
    }

    @Override
    public void verifyValue(String mode) {
        if (minCPS.getName().equals(mode) && minCPS.getValue() > maxCPS.getValue()) {
            maxCPS.setValue(minCPS.getValue());
        }
        if (maxCPS.getName().equals(mode) && minCPS.getValue() > maxCPS.getValue()) {
            minCPS.setValue(maxCPS.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return minCPS.getValue().equals(maxCPS.getValue()) ?
                new String[]{minCPS.getValue().toString()} :
                new String[]{String.format("%d-%d", minCPS.getValue(), maxCPS.getValue())};
    }
}
