package elara.module.world;

import elara.Elara;
import elara.event.EventTarget;
import elara.events.Render3DEvent;
import elara.mixin.IAccessorMinecraft;
import elara.mixin.IAccessorRenderManager;
import elara.module.Module;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ColorProperty;
import elara.property.properties.ModeProperty;
import elara.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.awt.Color;
import java.util.stream.Collectors;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"CUSTOM", "HUD"});
    public final ColorProperty chest = new ColorProperty("chest", new Color(255, 170, 0).getRGB(), () -> this.colorMode.getValue() == 0);
    public final ColorProperty trappedChest = new ColorProperty("trapped-chest", new Color(255, 43, 0).getRGB(), () -> this.colorMode.getValue() == 0);
    public final ColorProperty enderChest = new ColorProperty("ender-chest", new Color(26, 17, 0).getRGB(), () -> this.colorMode.getValue() == 0);
    public final BooleanProperty tracers = new BooleanProperty("tracers", false);

    public ChestESP() {
        super("ChestESP", false);
    }

    private int resolveHudColor() {
        try {
            elara.module.render.HUD hud = (elara.module.render.HUD) Elara.moduleManager.modules.get(elara.module.render.HUD.class);
            if (hud != null) return hud.getColor(System.currentTimeMillis()).getRGB();
        } catch (Throwable ignored) {}
        return -1;
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled()) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GlStateManager.pushMatrix();
            RenderUtil.enableRenderState();
            boolean useHud = this.colorMode.getValue() == 1;
            int hudRGB = useHud ? this.resolveHudColor() : -1;
            for (TileEntity chest : mc.theWorld.loadedTileEntityList.stream().filter(tileEntity -> tileEntity instanceof TileEntityChest || tileEntity instanceof TileEntityEnderChest).collect(Collectors.toList())) {
                Block block = mc.theWorld.getBlockState(chest.getPos()).getBlock();
                double minX, minZ, maxX, maxZ;
                int colorRGB;
                minX = minZ = 0.0625;
                maxX = maxZ = 0.9375;
                if (block instanceof BlockChest) {
                    if (useHud) {
                        colorRGB = hudRGB;
                    } else if (block.canProvidePower()) {
                        colorRGB = this.trappedChest.getValue();
                    } else {
                        colorRGB = this.chest.getValue();
                    }
                    EnumFacing facing = mc.theWorld.getBlockState(chest.getPos()).getValue(BlockChest.FACING);
                    switch (facing) {
                        case NORTH:
                            if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) {
                                minX -= 1;
                            }
                            break;
                        case SOUTH:
                            if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) {
                                maxX += 1;
                            }
                            break;
                        case WEST:
                            if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) {
                                maxZ += 1;
                            }
                            break;
                        case EAST:
                            if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) {
                                minZ -= 1;
                            }
                            break;
                        default:
                            continue;
                    }
                } else {
                    colorRGB = useHud ? hudRGB : this.enderChest.getValue();
                }
                int r = (colorRGB >> 16) & 0xFF;
                int g = (colorRGB >> 8) & 0xFF;
                int b = colorRGB & 0xFF;
                AxisAlignedBB aabb = new AxisAlignedBB(
                        (double) chest.getPos().getX() + minX,
                        (double) chest.getPos().getY() + 0.0,
                        (double) chest.getPos().getZ() + minZ,
                        (double) chest.getPos().getX() + maxX,
                        (double) chest.getPos().getY() + 0.875,
                        (double) chest.getPos().getZ() + maxZ
                )
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.drawBoundingBox(
                        aabb, r, g, b, 255, 1.5F
                );
                if (this.tracers.getValue()) {
                    Vec3 vec;
                    if (mc.gameSettings.thirdPersonView == 0) {
                        vec = new Vec3(0.0, 0.0, 1.0)
                                .rotatePitch(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.getRenderViewEntity().rotationPitch,
                                                                mc.getRenderViewEntity().prevRotationPitch,
                                                                ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                        )
                                                )
                                        )
                                )
                                .rotateYaw(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.getRenderViewEntity().rotationYaw,
                                                                mc.getRenderViewEntity().prevRotationYaw,
                                                                ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                        )
                                                )
                                        )
                                );
                    } else {
                        vec = new Vec3(0.0, 0.0, 0.0)
                                .rotatePitch(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                        )
                                                )
                                        )
                                )
                                .rotateYaw(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                        )
                                                )
                                        )
                                );
                    }
                    vec = new Vec3(vec.xCoord, vec.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
                    float opacity = (float) ((elara.module.render.Tracers) Elara.moduleManager.modules.get(elara.module.render.Tracers.class)).opacity.getValue() / 100.0F;
                    RenderUtil.drawLine3D(
                            vec,
                            (double) chest.getPos().getX() + 0.5,
                            (double) chest.getPos().getY() + 0.5,
                            (double) chest.getPos().getZ() + 0.5,
                            (float) r / 255.0F,
                            (float) g / 255.0F,
                            (float) b / 255.0F,
                            opacity,
                            1.5F
                    );
                }
            }
            RenderUtil.disableRenderState();
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
        }
    }
}
