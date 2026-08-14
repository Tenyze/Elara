package elara.module.render;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.TickEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;

    public final BooleanProperty autoF5 = new BooleanProperty("AutoF5", true);

    public boolean active = false;
    public float cameraYaw;
    public float cameraPitch;
    public float prevCameraYaw;
    public float prevCameraPitch;
    private int prevPerspective = 0;

    public FreeLook() {
        super("FreeLook", false, false, "Free camera view in third person", ModuleCategory.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnabled() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        this.prevPerspective = mc.gameSettings.thirdPersonView;
        if (this.autoF5.getValue()) {
            mc.gameSettings.thirdPersonView = 1;
        }
        this.cameraYaw = mc.thePlayer.rotationYaw;
        this.cameraPitch = mc.thePlayer.rotationPitch;
        this.prevCameraYaw = this.cameraYaw;
        this.prevCameraPitch = this.cameraPitch;
        this.active = true;
    }

    @Override
    public void onDisabled() {
        if (this.active) {
            this.active = false;
            Minecraft.getMinecraft().gameSettings.thirdPersonView = this.prevPerspective;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (!this.enabled) {
            if (this.active) {
                this.active = false;
                Minecraft.getMinecraft().gameSettings.thirdPersonView = this.prevPerspective;
            }
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        // 更新插值用 prev 值（渲染层用 prevCamera + (camera - prev) * partialTicks）
        this.prevCameraYaw = this.cameraYaw;
        this.prevCameraPitch = this.cameraPitch;

        if (!this.active) {
            this.active = true;
            this.prevPerspective = mc.gameSettings.thirdPersonView;
            if (this.autoF5.getValue()) {
                mc.gameSettings.thirdPersonView = 1;
            }
            this.cameraYaw = mc.thePlayer.rotationYaw;
            this.cameraPitch = mc.thePlayer.rotationPitch;
            this.prevCameraYaw = this.cameraYaw;
            this.prevCameraPitch = this.cameraPitch;
        }
    }

    public boolean isActive() {
        return this.enabled && this.active;
    }
}
