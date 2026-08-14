package elara.mixin;

import elara.Elara;
import elara.module.combat.Knockback;
import elara.module.movement.KeepSprint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityPlayer.class}, priority = 9999)
public abstract class MixinEntityPlayer extends MixinEntityLivingBase {

    @ModifyConstant(
            method = {"attackTargetEntityWithCurrentItem"},
            constant = {@Constant(
                    doubleValue = 0.6
            )}
    )
    private double attackTargetEntityWithCurrentItem(double speed) {
        if (Elara.moduleManager == null) {
            return speed;
        }
        if (Knockback.blinkActive) {
            return 1.0;
        }
        KeepSprint keepSprint = (KeepSprint) Elara.moduleManager.modules.get(KeepSprint.class);
        return keepSprint.isEnabled() && keepSprint.isAttackNoSlow()
                ? keepSprint.getSlowFactor()
                : speed;
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"
            )
    )
    private void setSprinnt(EntityPlayer entityPlayer, boolean boolean2) {
        if (Elara.moduleManager != null) {
            if (Knockback.blinkActive) {
                return;
            }
            KeepSprint keepSprint = (KeepSprint) Elara.moduleManager.modules.get(KeepSprint.class);
            if (!keepSprint.isEnabled() || !keepSprint.shouldKeepSprint()) {
                entityPlayer.setSprinting(boolean2);
            }
        }
    }
}