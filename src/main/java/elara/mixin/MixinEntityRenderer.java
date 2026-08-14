package elara.mixin;

import elara.Elara;
import elara.data.Box;
import elara.event.EventManager;
import elara.events.PickEvent;
import elara.events.RaytraceEvent;
import elara.events.Render3DEvent;
import elara.module.combat.KillAura;
import elara.module.misc.AntiDebuff;
import elara.module.utility.GhostHand;
import elara.module.misc.ViewClip;
import elara.module.exploit.NoHurtCam;
import elara.module.render.FreeLook;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityRenderer.class}, priority = 9999)
public abstract class MixinEntityRenderer {
    @Unique
    private Box<Integer> slot = null;
    @Unique
    private Box<ItemStack> using = null;
    @Unique
    private Box<Integer> useCount = null;
    @Shadow
    private Minecraft mc;
    @Shadow
    private float thirdPersonDistance;

    @Inject(
            method = {"updateCameraAndRender"},
            at = {@At("HEAD")}
    )
    private void updateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
        if (this.mc.thePlayer != null) {
            KillAura killAura = (KillAura) Elara.moduleManager.modules.get(KillAura.class);
            if (killAura != null && killAura.isEnabled() && killAura.isBlocking()) {
                this.using = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUse());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.mc.thePlayer.inventory.getCurrentItem());
                this.useCount = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUseCount());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(69000);
            }
        }
    }

    @Inject(
            method = {"updateCameraAndRender"},
            at = {@At("RETURN")}
    )
    private void postUpdateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
        if (this.using != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.using.value);
            this.using = null;
        }
        if (this.useCount != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(this.useCount.value);
            this.useCount = null;
        }
    }

    @Inject(
            method = {"updateRenderer"},
            at = {@At("HEAD")}
    )
    private void updateRenderer(CallbackInfo callbackInfo) {
    }

    @Inject(
            method = {"updateRenderer"},
            at = {@At("RETURN")}
    )
    private void postUpdateRenderer(CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
    }

    @Inject(
            method = {"renderWorldPass"},
            at = {@At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z",
                    shift = At.Shift.BEFORE
            )}
    )
    private void renderWorldPass(int integer, float float2, long long3, CallbackInfo callbackInfo) {
        EventManager.call(new Render3DEvent(float2));
    }

    @ModifyConstant(
            method = {"hurtCameraEffect"},
            constant = {@Constant(
                    floatValue = 14.0F,
                    ordinal = 0
            )}
    )
    private float hurtCameraEffect(float float1) {
        if (Elara.moduleManager == null) {
            return float1;
        } else {
            NoHurtCam noHurtCam = (NoHurtCam) Elara.moduleManager.modules.get(NoHurtCam.class);
            return noHurtCam != null && noHurtCam.isEnabled() ? float1 * (float) noHurtCam.multiplier.getValue().intValue() / 100.0F : float1;
        }
    }

    @ModifyConstant(
            method = {"getMouseOver"},
            constant = {@Constant(
                    doubleValue = 3.0,
                    ordinal = 1
            )}
    )
    private double getMouseOver(double range) {
        PickEvent event = new PickEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @ModifyVariable(
            method = {"getMouseOver"},
            at = @At("STORE"),
            name = {"d0"}
    )
    private double storeMouseOver(double range) {
        RaytraceEvent event = new RaytraceEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @Inject(
            method = {"getMouseOver"},
            at = {@At(
                    value = "INVOKE",
                    target = "Ljava/util/List;size()I",
                    ordinal = 0
            )},
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void a(
            float float1,
            CallbackInfo callbackInfo,
            Entity entity,
            double double4,
            double double5,
            Vec3 vec36,
            boolean boolean7,
            int integer8,
            Vec3 vec39,
            Vec3 vec310,
            Vec3 vec311,
            float float12,
            List<Entity> list,
            double double14,
            int integer15
    ) {
        if (Elara.moduleManager != null) {
            GhostHand event = (GhostHand) Elara.moduleManager.modules.get(GhostHand.class);
            if (event != null && event.isEnabled()) {
                list.removeIf(event::shouldSkip);
            }
        }
    }

    // ===== FreeLook: orientCamera 临时替换玩家朝向为相机朝向 =====
    @Unique private boolean flSaved = false;
    @Unique private float flSavedYaw, flSavedPitch, flSavedPrevYaw, flSavedPrevPitch;

    @Inject(
            method = {"orientCamera"},
            at = {@At("HEAD")}
    )
    private void freeLookOrientHead(float partialTicks, CallbackInfo ci) {
        FreeLook fl = FreeLook.INSTANCE;
        if (fl == null || !fl.isActive()) return;
        if (this.mc.getRenderViewEntity() != this.mc.thePlayer) return;
        this.flSaved = true;
        this.flSavedYaw = this.mc.thePlayer.rotationYaw;
        this.flSavedPitch = this.mc.thePlayer.rotationPitch;
        this.flSavedPrevYaw = this.mc.thePlayer.prevRotationYaw;
        this.flSavedPrevPitch = this.mc.thePlayer.prevRotationPitch;
        this.mc.thePlayer.rotationYaw = fl.cameraYaw;
        this.mc.thePlayer.rotationPitch = fl.cameraPitch;
        this.mc.thePlayer.prevRotationYaw = fl.prevCameraYaw;
        this.mc.thePlayer.prevRotationPitch = fl.prevCameraPitch;
    }

    @Inject(
            method = {"orientCamera"},
            at = {@At("RETURN")}
    )
    private void freeLookOrientReturn(float partialTicks, CallbackInfo ci) {
        if (this.flSaved) {
            this.mc.thePlayer.rotationYaw = this.flSavedYaw;
            this.mc.thePlayer.rotationPitch = this.flSavedPitch;
            this.mc.thePlayer.prevRotationYaw = this.flSavedPrevYaw;
            this.mc.thePlayer.prevRotationPitch = this.flSavedPrevPitch;
            this.flSaved = false;
        }
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Vec3;distanceTo(Lnet/minecraft/util/Vec3;)D"
            )
    )
    private double v(Vec3 vec31, Vec3 vec32) {
        if (Elara.moduleManager == null) {
            return vec31.distanceTo(vec32);
        } else {
            ViewClip viewClip = (ViewClip) Elara.moduleManager.modules.get(ViewClip.class);
            return viewClip != null && viewClip.isEnabled() ? (double) this.thirdPersonDistance : vec31.distanceTo(vec32);
        }
    }

    @Redirect(
            method = {"setupFog"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;getMaterial()Lnet/minecraft/block/material/Material;"
            )
    )
    private Material x(Block block) {
        if (Elara.moduleManager == null) {
            return block.getMaterial();
        } else {
            ViewClip viewClip = (ViewClip) Elara.moduleManager.modules.get(ViewClip.class);
            return viewClip != null && viewClip.isEnabled() ? Material.air : block.getMaterial();
        }
    }

    @Redirect(
            method = {"updateFogColor"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean y(EntityLivingBase entityLivingBase, Potion potion) {
        if (potion == Potion.blindness && Elara.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) Elara.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff != null && antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(
            method = {"setupFog"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean q(EntityLivingBase entityLivingBase, Potion potion) {
        if (potion == Potion.blindness && Elara.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) Elara.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff != null && antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(
            method = {"setupCameraTransform"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean c(EntityPlayerSP entityPlayerSP, Potion potion) {
        if (potion == Potion.confusion && Elara.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) Elara.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff != null && antiDebuff.isEnabled() && antiDebuff.nausea.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityPlayerSP).getActivePotionsMap().containsKey(potion.id);
    }
}