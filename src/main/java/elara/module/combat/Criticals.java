package elara.module.combat;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.AttackEvent;
import elara.events.PacketEvent;
import elara.mixin.IAccessorC03PacketPlayer;
import elara.mixin.IAccessorEntity;
import elara.module.Module;
import elara.module.movement.Fly;
import elara.property.properties.FloatProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import elara.property.properties.BooleanProperty;
import elara.util.MSTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Criticals extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Packet", "NCPPacket", "BlocksMC", "BlocksMC2", "NoGround", "Hop", "TPHop", "Jump", "LowJump", "CustomMotion", "Visual"});
    public final IntProperty delay = new IntProperty("Delay", 0, 0, 500);
    public final IntProperty hurtTime = new IntProperty("HurtTime", 10, 0, 10);
    public final FloatProperty customMotionY = new FloatProperty("Custom-Y", 0.2f, 0.01f, 0.42f);
    public final BooleanProperty onlyInAir = new BooleanProperty("OnlyInAir", false);

    private final MSTimer msTimer = new MSTimer();

    public Criticals() {
        super("Criticals", false);

        mode.setCategory("Combat");
        delay.setCategory("Timing");
        hurtTime.setCategory("Timing");
        customMotionY.setCategory("Movement");
        onlyInAir.setCategory("Conditions");
    }

    @Override
    public void onEnabled() {
        if (this.mode.getValue() == 4 && mc.thePlayer != null) {
            mc.thePlayer.jump();
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        if (!(event.getTarget() instanceof EntityLivingBase)) return;

        EntityLivingBase entity = (EntityLivingBase) event.getTarget();

        boolean isInWeb = ((IAccessorEntity) mc.thePlayer).getIsInWeb();
        boolean isInLiquid = mc.thePlayer.isInWater() || mc.thePlayer.isInLava();

        if (!mc.thePlayer.onGround || mc.thePlayer.isOnLadder() || isInWeb || isInLiquid
                || mc.thePlayer.ridingEntity != null || entity.hurtTime > this.hurtTime.getValue()
                || !this.msTimer.hasTimePassed(this.delay.getValue())) {
            return;
        }

        Fly fly = (Fly) Elara.moduleManager.getModule(Fly.class);
        if (fly != null && fly.isEnabled()) {
            return;
        }

        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY;
        double z = mc.thePlayer.posZ;

        switch (this.mode.getValue()) {
            case 0:
                if (this.onlyInAir.getValue() && mc.thePlayer.onGround) {
                    return;
                }
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625, z, true));
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                mc.thePlayer.onCriticalHit(entity);
                break;

            case 1:
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.11, z, false));
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.1100013579, z, false));
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0000013579, z, false));
                mc.thePlayer.onCriticalHit(entity);
                break;

            case 2:
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.001091981, z, true));
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                break;

            case 3:
                if (mc.thePlayer.ticksExisted % 4 == 0) {
                    mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0011, z, true));
                    mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
                }
                break;

            case 5:
                mc.thePlayer.motionY = 0.1;
                mc.thePlayer.fallDistance = 0.1f;
                mc.thePlayer.onGround = false;
                break;

            case 6:
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.02, z, false));
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.01, z, false));
                mc.thePlayer.setPosition(x, y + 0.01, z);
                break;

            case 7:
                mc.thePlayer.motionY = 0.42;
                break;

            case 8:
                mc.thePlayer.motionY = 0.3425;
                break;

            case 9:
                mc.thePlayer.motionY = this.customMotionY.getValue();
                break;

            case 10:
                mc.thePlayer.onCriticalHit(entity);
                break;
        }

        this.msTimer.reset();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() == EventType.SEND) {
            if (this.mode.getValue() == 4 && event.getPacket() instanceof C03PacketPlayer) {
                ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}