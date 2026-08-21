package elara.module.world;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.*;
import elara.management.RotationState;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.mixin.IAccessorRenderManager;
import elara.module.render.HUD;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ColorProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import elara.property.properties.PercentProperty;
import elara.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875,
            0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
    };

    private final ArrayList<PlacedBlock> placedBlocks = new ArrayList<>();

    public final ModeProperty rotationMode = new ModeProperty("rotations", 2,
            new String[]{"NONE", "DEFAULT", "BACKWARDS", "SIDEWAYS"});
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1,
            new String[]{"NONE", "SILENT"});
    public final ModeProperty sprintMode = new ModeProperty("sprint", 0,
            new String[]{"NONE", "VANILLA"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final ModeProperty tower = new ModeProperty("tower", 0,
            new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final ModeProperty keepY = new ModeProperty("keep-y", 0,
            new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final BooleanProperty keepYonPress = new BooleanProperty("keep-y-on-press", false,
            () -> this.keepY.getValue() != 0);
    public final BooleanProperty disableWhileJumpActive = new BooleanProperty("no-keep-y-on-jump-potion", false,
            () -> this.keepY.getValue() != 0);
    public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
    public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
    public final BooleanProperty showBlockPlaces = new BooleanProperty("show-block-places", false);
    public final FloatProperty blockPlaceDuration = new FloatProperty("block-place-duration", 1.0F, 0.05F, 3.0F,
            this.showBlockPlaces::getValue);
    public final ModeProperty blockPlaceColorMode = new ModeProperty("block-place-color", 0,
            new String[]{"CUSTOM", "HUD"}, this.showBlockPlaces::getValue);
    public final ColorProperty blockPlaceFillColor = new ColorProperty("block-place-fill-color", 65280,
            () -> this.showBlockPlaces.getValue() && this.blockPlaceColorMode.getValue() == 0);
    public final PercentProperty blockPlaceFillAlpha = new PercentProperty("block-place-fill-alpha", 35,
            () -> this.showBlockPlaces.getValue() && this.blockPlaceColorMode.getValue() == 0);
    public final ColorProperty blockPlaceOutlineColor = new ColorProperty("block-place-outline-color", 65280,
            () -> this.showBlockPlaces.getValue() && this.blockPlaceColorMode.getValue() == 0);
    public final PercentProperty blockPlaceOutlineAlpha = new PercentProperty("block-place-outline-alpha", 100,
            () -> this.showBlockPlaces.getValue() && this.blockPlaceColorMode.getValue() == 0);
    public final ModeProperty counterPosX = new ModeProperty("counter-position-x", 1,
            new String[]{"LEFT", "MIDDLE", "RIGHT"}, () -> this.blockCounter.getValue());
    public final ModeProperty counterPosY = new ModeProperty("counter-position-y", 1,
            new String[]{"TOP", "MIDDLE", "BOTTOM"}, () -> this.blockCounter.getValue());
    public final FloatProperty counterScale = new FloatProperty("counter-scale", 1.0F, 0.5F, 1.5F,
            () -> this.blockCounter.getValue());
    public final IntProperty counterOffX = new IntProperty("counter-offset-x", 0, -255, 255,
            () -> this.blockCounter.getValue());
    public final IntProperty counterOffY = new IntProperty("counter-offset-y", 0, -255, 255,
            () -> this.blockCounter.getValue());
    public final BooleanProperty counterBackground = new BooleanProperty("counter-background", true,
            () -> this.blockCounter.getValue());
    public final PercentProperty counterBgAlpha = new PercentProperty("counter-bg-alpha", 25,
            () -> this.blockCounter.getValue() && this.counterBackground.getValue());

    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private int spoofBlockSlot = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int towerTick = 0;
    private int towerDelay = 0;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private EnumFacing targetFacing = null;

    public Scaffold() {
        super("Scaffold", false, false, "Automatically places blocks beneath you", ModuleCategory.WORLD);
    }

    private boolean shouldStopSprint() {
        if (this.isTowering()) {
            return false;
        } else {
            boolean stage = this.keepY.getValue() == 1 || this.keepY.getValue() == 2;
            return (!stage || this.stage <= 0) && this.sprintMode.getValue() == 0;
        }
    }

    private boolean canPlace() {
        BedBreaker bedBreaker = (BedBreaker) Elara.moduleManager.modules.get(BedBreaker.class);
        if (bedBreaker != null && bedBreaker.isEnabled() && bedBreaker.isReady()) {
            return false;
        }
        return true;
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter(
                            (double) blockPos3.getX() + 0.5,
                            (double) blockPos3.getY() + 0.5,
                            (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset ||
                            (distance == offset && facing == EnumFacing.UP)) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        } else {
            ArrayList<BlockPos> positions = new ArrayList<>();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 0; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = targetPos.add(x, y, z);
                        if (!BlockUtil.isReplaceable(pos)
                                && !BlockUtil.isInteractable(pos)
                                && mc.thePlayer.getDistance(
                                (double) pos.getX() + 0.5,
                                (double) pos.getY() + 0.5,
                                (double) pos.getZ() + 0.5)
                                <= (double) mc.playerController.getBlockReachDistance()
                                && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                            for (EnumFacing facing : EnumFacing.VALUES) {
                                if (facing != EnumFacing.DOWN) {
                                    BlockPos blockPos = pos.offset(facing);
                                    if (BlockUtil.isReplaceable(blockPos)) {
                                        positions.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (positions.isEmpty()) {
                return null;
            } else {
                positions.sort(Comparator.comparingDouble(
                        o -> o.distanceSqToCenter(
                                (double) targetPos.getX() + 0.5,
                                (double) targetPos.getY() + 0.5,
                                (double) targetPos.getZ() + 0.5)));
                BlockPos blockPos = positions.get(0);
                EnumFacing facing = this.getBestFacing(blockPos, targetPos);
                return facing == null ? null : new BlockData(blockPos, facing);
            }
        }
    }

    private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (this.itemSpoof.getValue() && this.spoofBlockSlot >= 0) {
            // itemSpoof: 临时切换到方块槽位放置，然后立即恢复
            ItemStack blockStack = mc.thePlayer.inventory.getStackInSlot(this.spoofBlockSlot);
            if (ItemUtil.isBlock(blockStack) && this.blockCount > 0) {
                int originalSlot = mc.thePlayer.inventory.currentItem;
                mc.thePlayer.inventory.currentItem = this.spoofBlockSlot;
                if (mc.playerController.onPlayerRightClick(
                        mc.thePlayer, mc.theWorld, blockStack,
                        blockPos, enumFacing, vec3)) {
                    if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                        this.blockCount--;
                    }
                    if (this.swing.getValue()) {
                        mc.thePlayer.swingItem();
                    } else {
                        PacketUtil.sendPacket(new C0APacketAnimation());
                    }
                    if (this.showBlockPlaces.getValue()) {
                        this.placedBlocks.add(new PlacedBlock(blockPos.offset(enumFacing)));
                    }
                }
                // 立即恢复客户端槽位并通知服务器
                mc.thePlayer.inventory.currentItem = originalSlot;
                PacketUtil.sendPacket(new C09PacketHeldItemChange(originalSlot));
            }
            return;
        }
        if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                    blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                    this.blockCount--;
                }
                if (this.swing.getValue()) {
                    mc.thePlayer.swingItem();
                } else {
                    PacketUtil.sendPacket(new C0APacketAnimation());
                }
                if (this.showBlockPlaces.getValue()) {
                    this.placedBlocks.add(new PlacedBlock(blockPos.offset(enumFacing)));
                }
            }
        }
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) this.airMotion.getValue() / 100.0F;
        } else {
            return MoveUtil.getSpeedLevel() > 0
                    ? (float) this.speedMotion.getValue() / 100.0F
                    : (float) this.groundMotion.getValue() / 100.0F;
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw,
                (float) MoveUtil.getForwardValue(),
                (float) MoveUtil.getLeftValue());
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
            boolean keepY = this.keepY.getValue() == 3;
            boolean tower = this.tower.getValue() == 3;
            return (keepY && this.stage > 0) || (tower && mc.gameSettings.keyBindJump.isKeyDown());
        } else {
            return false;
        }
    }

    public int getSlot() {
        return this.lastSlot;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                if (this.stage == 0
                        && this.keepY.getValue() != 0
                        && (!this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
                        && (!this.disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
                        && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.stage = 1;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
            }
            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            if (this.itemSpoof.getValue()) {
                                // itemSpoof: 不直接切换客户端槽位，仅记录并欺骗服务器
                                this.spoofBlockSlot = hotbarSlot;
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(hotbarSlot));
                            } else {
                                mc.thePlayer.inventory.currentItem = hotbarSlot;
                            }
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F *
                                                                  ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());
                if (!this.canRotate) {
                    switch (this.rotationMode.getValue()) {
                        case 1:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 2:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 3:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                    }
                }
                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                if (blockData != null) {
                    double[] x = placeOffsets;
                    double[] y = placeOffsets;
                    double[] z = placeOffsets;
                    switch (blockData.facing()) {
                        case NORTH:
                            z = new double[]{0.0};
                            break;
                        case EAST:
                            x = new double[]{1.0};
                            break;
                        case SOUTH:
                            z = new double[]{1.0};
                            break;
                        case WEST:
                            x = new double[]{0.0};
                            break;
                        case DOWN:
                            y = new double[]{0.0};
                            break;
                        case UP:
                            y = new double[]{1.0};
                            break;
                    }
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    float bestDiff = 0.0F;
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY
                                        - (double) mc.thePlayer.getEyeHeight();
                                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                float[] rotations = RotationUtil.getRotationsToRelative(relX, relY, relZ, baseYaw, this.pitch);
                                MovingObjectPosition mop = RotationUtil.rayTrace(
                                        rotations[0], rotations[1],
                                        mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop != null
                                        && mop.typeOfHit == MovingObjectType.BLOCK
                                        && mop.getBlockPos().equals(blockData.blockPos())
                                        && mop.sideHit == blockData.facing()) {
                                    float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                    if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                        bestYaw = rotations[0];
                                        bestPitch = rotations[1];
                                        bestDiff = totalDiff;
                                        hitVec = mop.hitVec;
                                    }
                                }
                            }
                        }
                    }
                    if (bestYaw != -180.0F || bestPitch != 0.0F) {
                        this.yaw = bestYaw;
                        this.pitch = bestPitch;
                        this.canRotate = true;
                    }
                }
                if (this.canRotate && MoveUtil.isForwardPressed()
                        && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    switch (this.rotationMode.getValue()) {
                        case 2:
                            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            break;
                        case 3:
                            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            break;
                    }
                }
                if (this.rotationMode.getValue() != 0) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    if (this.towering && (mc.thePlayer.motionY > 0.0
                            || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                        float tolerance = this.rotationTick >= 2
                                ? RandomUtil.nextFloat(90.0F, 95.0F)
                                : RandomUtil.nextFloat(30.0F, 35.0F);
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    if (this.isTowering()) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                        targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                }
                if (blockData != null && hitVec != null && this.rotationTick <= 0) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                    if (this.multiplace.getValue()) {
                        for (int i = 0; i < 3; i++) {
                            blockData = this.getBlockData();
                            if (blockData == null) {
                                break;
                            }
                            MovingObjectPosition mop = RotationUtil.rayTrace(
                                    this.yaw, this.pitch,
                                    mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null
                                    && mop.typeOfHit == MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            } else {
                                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                                double dx = hitVec.xCoord - mc.thePlayer.posX;
                                double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double dz = hitVec.zCoord - mc.thePlayer.posZ;
                                float[] rotations = RotationUtil.getRotationsToRelative(dx, dy, dz, event.getYaw(), event.getPitch());
                                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F)
                                        || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                                    break;
                                }
                                mop = RotationUtil.rayTrace(rotations[0], rotations[1],
                                        mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop == null
                                        || mop.typeOfHit != MovingObjectType.BLOCK
                                        || !mop.getBlockPos().equals(blockData.blockPos())
                                        || mop.sideHit != blockData.facing()) {
                                    break;
                                }
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            }
                        }
                    }
                }
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        this.place(belowPlayer, this.targetFacing, hitVec);
                    }
                    this.targetFacing = null;
                } else if (this.keepY.getValue() == 2 && this.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        blockData = this.getBlockData();
                        if (blockData != null && this.rotationTick <= 0) {
                            hitVec = BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (!mc.thePlayer.isCollidedHorizontally
                    && mc.thePlayer.hurtTime <= 5
                    && !mc.thePlayer.isPotionActive(Potion.jump)
                    && mc.gameSettings.keyBindJump.isKeyDown()
                    && ItemUtil.isHoldingBlock()) {
                int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
                switch (this.tower.getValue()) {
                    case 1:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    this.towerTick = 2;
                                    mc.thePlayer.motionY = 0.42F;
                                    if (MoveUtil.isForwardPressed()) {
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                    } else {
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                    }
                                    return;
                                } else {
                                    this.towerTick = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                                return;
                            case 3:
                                this.towerTick = 1;
                                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                return;
                            default:
                                this.towerTick = 0;
                                return;
                        }
                    case 2:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    if (!MoveUtil.isForwardPressed()) {
                                        this.towerDelay = 2;
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                                        double distance = this.distanceToEdge(facing);
                                        if (distance > 0.1) {
                                            if (mc.thePlayer.onGround) {
                                                Vec3i directionVec = facing.getDirectionVec();
                                                double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                                                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                                AxisAlignedBB nextBox = mc.thePlayer
                                                        .getEntityBoundingBox()
                                                        .offset((double) directionVec.getX() * (offset - jitter), 0.0,
                                                                (double) directionVec.getZ() * (offset - jitter));
                                                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                                    mc.thePlayer.motionY = -0.0784000015258789;
                                                    mc.thePlayer
                                                            .setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0,
                                                                    nextBox.minY, nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                                }
                                                return;
                                            }
                                        } else {
                                            this.towerTick = 2;
                                            this.targetFacing = facing;
                                            mc.thePlayer.motionY = 0.42F;
                                        }
                                        return;
                                    } else {
                                        this.towerTick = 2;
                                        this.towerDelay++;
                                        mc.thePlayer.motionY = 0.42F;
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                        return;
                                    }
                                } else {
                                    this.towerTick = 0;
                                    this.towerDelay = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                                return;
                            case 3:
                                if (this.towerDelay >= 4) {
                                    this.towerTick = 4;
                                    this.towerDelay = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                }
                                return;
                            case 4:
                                this.towerTick = 5;
                                return;
                            case 5:
                                if (!PlayerUtil.isAirBelow()) {
                                    this.towerTick = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                }
                                return;
                            default:
                                this.towerTick = 0;
                                this.towerDelay = 0;
                                return;
                        }
                    default:
                        this.towerTick = 0;
                        this.towerDelay = 0;
                }
            } else {
                this.towerTick = 0;
                this.towerDelay = 0;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 3.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            float speed = this.getSpeed();
            if (speed != 1.0F) {
                if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                    mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveForward
                            * (1.0F / (float) Math.sqrt(2.0));
                    mc.thePlayer.movementInput.moveStrafe = mc.thePlayer.movementInput.moveStrafe
                            * (1.0F / (float) Math.sqrt(2.0));
                }
                mc.thePlayer.movementInput.moveForward *= speed;
                mc.thePlayer.movementInput.moveStrafe *= speed;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.isEnabled() && this.safeWalk.getValue()) {
            if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0
                    && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || !this.blockCounter.getValue()) {
            return;
        }
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                Item item = stack.getItem();
                if (item instanceof ItemBlock) {
                    Block block = ((ItemBlock) item).getBlock();
                    if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                        count += stack.stackSize;
                    }
                }
            }
        }
        HUD hud = (HUD) Elara.moduleManager.modules.get(HUD.class);
        float sc = this.counterScale.getValue();
        ScaledResolution sr = new ScaledResolution(mc);
        String text = String.format("%d block%s left", count, count != 1 ? "s" : "");
        int textWidth = mc.fontRendererObj.getStringWidth(text);
        int textHeight = mc.fontRendererObj.FONT_HEIGHT;
        float padX = 3.0F;
        float padY = 2.0F;
        float boxW = (float) textWidth + padX * 2.0F;
        float boxH = (float) textHeight + padY * 2.0F;
        float x = (float) this.counterOffX.getValue() / sc;
        float y = (float) this.counterOffY.getValue() / sc;
        float scaledW = (float) sr.getScaledWidth() / sc;
        float scaledH = (float) sr.getScaledHeight() / sc;
        switch (this.counterPosX.getValue()) {
            case 0:
                break;
            case 1:
                x += scaledW / 2.0F - boxW / 2.0F;
                break;
            case 2:
                x = scaledW - boxW - (float) this.counterOffX.getValue() / sc;
        }
        switch (this.counterPosY.getValue()) {
            case 0:
                break;
            case 1:
                y += scaledH / 2.0F - boxH / 2.0F;
                break;
            case 2:
                y = scaledH - boxH - (float) this.counterOffY.getValue() / sc;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(sc, sc, 0.0F);
        GlStateManager.translate(x, y, 0.0F);
        RenderUtil.enableRenderState();
        if (this.counterBackground.getValue()) {
            int bgAlpha = (int) ((float) this.counterBgAlpha.getValue() / 100.0F * 255.0F);
            int bgColor = new Color(0, 0, 0, bgAlpha).getRGB();
            RenderUtil.drawRect(0.0F, 0.0F, boxW, boxH, bgColor);
        }
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRendererObj.drawString(
                text, padX, padY,
                (count > 0 ? Color.WHITE.getRGB() : new Color(255, 85, 85).getRGB()) | 0xFF000000,
                hud != null && hud.shadow.getValue());
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.showBlockPlaces.getValue() || this.placedBlocks.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long dur = (long) (this.blockPlaceDuration.getValue() * 1000.0F);
        HUD hud = (HUD) Elara.moduleManager.modules.get(HUD.class);
        double rpX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double rpY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double rpZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        RenderUtil.enableRenderState();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        Iterator<PlacedBlock> it = this.placedBlocks.iterator();
        while (it.hasNext()) {
            int oa;
            int ob;
            int og;
            int or_;
            int fa;
            int fb;
            int fg;
            int fr;
            PlacedBlock pb = it.next();
            long age = now - pb.placedAt;
            if (age > dur) {
                it.remove();
                continue;
            }
            float t = (float) age / (float) dur;
            float eased = 1.0F - (1.0F - t) * (1.0F - t);
            float scale = 1.0F - eased * 0.3F;
            float alpha = t > 0.7F ? 1.0F - (t - 0.7F) / 0.3F : 1.0F;
            alpha = Math.max(0.0F, alpha);
            if (this.blockPlaceColorMode.getValue() == 1 && hud != null) {
                Color c = hud.getColor(now, pb.placedAt);
                fr = c.getRed();
                fg = c.getGreen();
                fb = c.getBlue();
                fa = (int) (alpha * 90.0F);
                or_ = fr;
                og = fg;
                ob = fb;
                oa = (int) (alpha * 255.0F);
            } else {
                int fRaw = this.blockPlaceFillColor.getValue();
                fr = fRaw >> 16 & 0xFF;
                fg = fRaw >> 8 & 0xFF;
                fb = fRaw & 0xFF;
                fa = (int) ((float) this.blockPlaceFillAlpha.getValue() / 100.0F * 255.0F * alpha);
                int oRaw = this.blockPlaceOutlineColor.getValue();
                or_ = oRaw >> 16 & 0xFF;
                og = oRaw >> 8 & 0xFF;
                ob = oRaw & 0xFF;
                oa = (int) ((float) this.blockPlaceOutlineAlpha.getValue() / 100.0F * 255.0F * alpha);
            }
            AxisAlignedBB aabb = new AxisAlignedBB(
                    (double) pb.pos.getX(), (double) pb.pos.getY(), (double) pb.pos.getZ(),
                    (double) (pb.pos.getX() + 1), (double) (pb.pos.getY() + 1), (double) (pb.pos.getZ() + 1))
                    .offset(-rpX, -rpY, -rpZ);
            double cx = (double) pb.pos.getX() + 0.5 - rpX;
            double cy2 = (double) pb.pos.getY() + 0.5 - rpY;
            double cz = (double) pb.pos.getZ() + 0.5 - rpZ;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy2, cz);
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.translate(-cx, -cy2, -cz);
            wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(aabb.minX, aabb.minY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.minY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.minY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.minY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.maxY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.maxY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.maxY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.maxY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.minY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.maxY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.maxY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.minY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.minY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.maxY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.maxY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.minY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.minY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.maxY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.maxY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.minX, aabb.minY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.minY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.maxY, aabb.minZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.maxY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            wr.pos(aabb.maxX, aabb.minY, aabb.maxZ).color(fr, fg, fb, fa).endVertex();
            tess.draw();
            RenderUtil.drawBoundingBox(aabb, or_, og, ob, oa, 1.5F);
            GlStateManager.popMatrix();
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
   public void onSwap(SwapItemEvent event) {
      if (this.isEnabled() && this.itemSpoof.getValue()) {
         this.lastSlot = event.setSlot(this.lastSlot);
         event.setCancelled(true);
      }
   }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.spoofBlockSlot = -1;
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
        this.placedBlocks.clear();
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
            if (this.itemSpoof.getValue()) {
                PacketUtil.sendPacket(new C09PacketHeldItemChange(this.lastSlot));
            }
        }
        this.spoofBlockSlot = -1;
        this.placedBlocks.clear();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.rotationMode.getModeString()};
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }

    private static class PlacedBlock {
        final BlockPos pos;
        final long placedAt;

        PlacedBlock(BlockPos pos) {
            this.pos = pos;
            this.placedAt = System.currentTimeMillis();
        }
    }
}
