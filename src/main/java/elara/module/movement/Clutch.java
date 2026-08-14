package elara.module.movement;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.MoveInputEvent;
import elara.events.UpdateEvent;
import elara.management.RotationState;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.*;
import elara.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.*;

public class Clutch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double HALF_WIDTH = 0.3;
    private static final double[][] CORNERS = {{-HALF_WIDTH, -HALF_WIDTH}, {HALF_WIDTH, -HALF_WIDTH}, {-HALF_WIDTH, HALF_WIDTH}, {HALF_WIDTH, HALF_WIDTH}};

    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();

    static {
        BLOCK_SCORE.put("obsidian", 0);
        BLOCK_SCORE.put("end_stone", 1);
        BLOCK_SCORE.put("planks", 2);
        BLOCK_SCORE.put("log", 2);
        BLOCK_SCORE.put("log2", 2);
        BLOCK_SCORE.put("glass", 3);
        BLOCK_SCORE.put("stained_glass", 3);
        BLOCK_SCORE.put("hardened_clay", 4);
        BLOCK_SCORE.put("stained_hardened_clay", 4);
        BLOCK_SCORE.put("stone", 5);
        BLOCK_SCORE.put("wool", 5);
    }

    public final FloatProperty reach = new FloatProperty("reach", 4.5F, 0.5F, 6.0F);
    public final FloatProperty speed = new FloatProperty("speed", 8.0F, 0.0F, 100.0F);
    public final FloatProperty snapbackSpeed = new FloatProperty("snapback-speed", 12.0F, 0.0F, 100.0F);
    public final FloatProperty maxDistance = new FloatProperty("max-distance", 1.0F, 0.0F, 20.0F);
    public final FloatProperty rotationTolerance = new FloatProperty("rotation-tolerance", 25.0F, 1.0F, 100.0F);
    public final BooleanProperty autoClutch = new BooleanProperty("auto-clutch", true);
    public final FloatProperty minimumFallDistance = new FloatProperty("min-fall-distance", 3.0F, 1.0F, 20.0F);
    public final BooleanProperty simulateFuturePosition = new BooleanProperty("simulate-future", true);
    public final IntProperty selectKeybind = new IntProperty("select-key", 0, -1, 200);
    public final BooleanProperty autoScaf = new BooleanProperty("Auto SCAF", false);

    private boolean scafToggledByClutch = false;
    private BlockPos placeAtBlock;
    private EnumFacing hitSide;
    private Vec3 hitVec;
    private boolean placeQueued;
    private boolean placing;
    private boolean slotWasSwapped;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private float aimYaw;
    private float aimPitch;
    private BlockPos targetHitPos;
    private EnumFacing targetSide;
    private boolean hasAim;
    private boolean resetting;
    private BlockPos lastPlaced;
    private int clutchBlocksPlaced;
    private boolean autoClutchActive;
    private boolean autoClutchChecking;
    private int autoClutchCheckCounter;
    private boolean autoClutchLandedGuard;
    private int autoClutchLandedTick;
    private int prevHurtTime = -1;
    private float currentYaw;
    private float currentPitch;

    public Clutch() {
        super("Clutch", false, false, "Auto-place blocks when about to fall", ModuleCategory.MOVEMENT);

        reach.setCategory("Settings");
        speed.setCategory("Settings");
        snapbackSpeed.setCategory("Settings");
        maxDistance.setCategory("Settings");
        rotationTolerance.setCategory("Settings");
        autoClutch.setCategory("Auto");
        minimumFallDistance.setCategory("Auto");
        simulateFuturePosition.setCategory("Settings");
        selectKeybind.setCategory("Key");
        autoScaf.setCategory("Auto");
    }

    @Override
    public void onEnabled() {
        reset();
    }

    @Override
    public void onDisabled() {
        clearAim(false);
        disablePlacing(true);
        placeQueued = false;
        autoClutchActive = false;
        autoClutchChecking = false;
        autoClutchLandedGuard = false;
        resetting = false;
        hasAim = false;
        placing = false;
        autoClutchCheckCounter = 0;
        autoClutchLandedTick = 0;
        prevHurtTime = -1;
        if (scafToggledByClutch) {
            Module scaf = Elara.moduleManager.getModule(elara.module.world.Scaffold.class);
            if (scaf != null && scaf.isEnabled()) {
                scaf.setEnabled(false);
            }
            scafToggledByClutch = false;
        }
    }

    private void reset() {
        hasAim = false;
        resetting = false;
        clutchBlocksPlaced = 0;
        autoClutchActive = false;
        autoClutchChecking = false;
        autoClutchCheckCounter = 0;
        autoClutchLandedGuard = false;
        autoClutchLandedTick = 0;
        prevHurtTime = -1;
        placeAtBlock = null;
        hitSide = null;
        hitVec = null;
        lastPlaced = null;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        currentYaw = event.getYaw();
        currentPitch = event.getPitch();

        runPrePlayerInteract();

        if (mc.currentScreen != null) {
            disablePlacing(false);
            return;
        }

        if (resetting) {
            aimYaw = mc.thePlayer.rotationYaw;
            aimPitch = mc.thePlayer.rotationPitch;
            float[] smoothed = getRotationsSmoothed(currentYaw, currentPitch, aimYaw, aimPitch, true);
            if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - aimYaw)) < 0.5f && Math.abs(smoothed[1] - aimPitch) < 0.5f) {
                resetting = false;
                restoreInputs();
                return;
            }
            event.setRotation(smoothed[0], smoothed[1], 1);
            event.setPervRotation(smoothed[0], 1);
            return;
        }

        if (!hasAim) return;

        float[] smoothed = getRotationsSmoothed(currentYaw, currentPitch, aimYaw, aimPitch, false);

        if (placing && targetHitPos != null) {
            MovingObjectPosition mop = RotationUtil.rayTrace(smoothed[0], smoothed[1], reach.getValue(), 1.0F);
            if (mop != null && targetHitPos.equals(mop.getBlockPos()) && targetSide == mop.sideHit) {
                int maxBlocks = (int) maxDistance.getValue().floatValue();
                if (maxBlocks == 0 || clutchBlocksPlaced < maxBlocks) {
                    double tolerance = rotationTolerance.getValue();
                    float serverYaw = RotationUtil.wrapAngleDiff(currentYaw, smoothed[0]);
                    float serverPitch = smoothed[1];
                    if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - serverYaw)) <= tolerance
                            && Math.abs(smoothed[1] - serverPitch) <= tolerance) {
                        placeAtBlock = mop.getBlockPos();
                        hitSide = mop.sideHit;
                        hitVec = mop.hitVec;
                        placeQueued = true;
                    }
                }
            }
        }

        event.setRotation(smoothed[0], smoothed[1], 1);
        event.setPervRotation(smoothed[0], 1);
    }

    @EventTarget
    public void onUpdatePost(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.POST) return;
        if (!placeQueued) return;

        placeQueued = false;
        if (placeAtBlock != null && hitSide != null && hitVec != null) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), placeAtBlock, hitSide, hitVec)) {
                if (hitSide != EnumFacing.UP) clutchBlocksPlaced++;
                lastPlaced = placeAtBlock;
                mc.thePlayer.swingItem();
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()
                && RotationState.isActived()
                && RotationState.getPriority() == 1.0F
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    private void runPrePlayerInteract() {
        if (mc.thePlayer.onGround) clutchBlocksPlaced = 0;
        int ticksExisted = mc.thePlayer.ticksExisted;

        updateAutoClutch(ticksExisted);

        int keybind = selectKeybind.getValue();
        boolean keyPressed = keybind != 0 && KeyBindUtil.isKeyDown(keybind);
        boolean active = keyPressed || autoClutchActive;

        if (mc.currentScreen != null || !active) {
            clearAim(true);
            disablePlacing(false);
            return;
        }

        // Auto SCAF 协作模式：放置+转头完全交给 Scaffold，Clutch 仅作为 fall-void 检测（已在 updateAutoClutch 中处理开关）
        if (autoScaf.getValue()) {
            clearAim(false);
            disablePlacing(false);
            return;
        }

        // 高度安全判定：
        //   1. 玩家 onGround 且不会即将坠落 → 安全
        //   2. 上升期 (motionY > 0.01) + 脚下 minFall+1 格内有地面 → 跳起来自救场景，直接安全退出
        //   3. 下落初期 (fallDistance < 1.0, motionY >= -0.1) + 脚下 minFall 内有实地面 → 仍在可控高度，不需要自救
        double minFall = minimumFallDistance.getValue();
        double checkDepth = Math.max(3.0, minFall + 1.0);

        // 上升期硬屏蔽：只要还在往上跳，且脚下不远处有地面，就一定不放
        if (mc.thePlayer.motionY > 0.01 && hasSolidGroundBeneath(checkDepth)) {
            clearAim(true);
            disablePlacing(false);
            return;
        }

        boolean onSolidGround = mc.thePlayer.onGround
                || (mc.thePlayer.motionY >= -0.01 && hasSolidGroundBeneath(checkDepth))
                || (mc.thePlayer.fallDistance < 1.0F && mc.thePlayer.motionY >= -0.1
                && getAirBeneathFeet((int) Math.ceil(checkDepth)) < minFall);
        if (onSolidGround && !willFallSoon() && !willFallFar(minFall)) {
            clearAim(true);
            disablePlacing(false);
            return;
        }

        BlockPos below = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!canPlaceThrough(below)) {
            disablePlacing(false);
            return;
        }

        int weakSlot = pickBlockSlot();
        if (weakSlot == -1) {
            disablePlacing(false);
            return;
        }

        plannedSlot = weakSlot;
        AimResult target = clutchAim();
        if (target != null) {
            targetHitPos = target.ray.getBlockPos();
            targetSide = target.ray.sideHit;
            aimYaw = target.yaw;
            aimPitch = target.pitch;
            hasAim = true;
            resetting = false;
        }

        if (hasAim && !placing) enablePlacing();

        if (placing || resetting || hasAim) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            equipPlannedSlot();
        }
    }

    private void updateAutoClutch(int ticksExisted) {
        if (!autoClutch.getValue()) {
            autoClutchActive = false;
            autoClutchChecking = false;
            autoClutchLandedGuard = false;
            prevHurtTime = mc.thePlayer.hurtTime;
            return;
        }

        int curHurtTime = mc.thePlayer.hurtTime;
        if (curHurtTime > prevHurtTime) {
            autoClutchChecking = true;
            autoClutchCheckCounter = 0;
            autoClutchLandedGuard = false;
        }
        prevHurtTime = curHurtTime;

        // 先算一次空距（复用）：扫描 minFall + 3 格，足够判断所有条件
        double minFall = minimumFallDistance.getValue();
        int scanDepth = Math.max(8, (int) Math.ceil(minFall) + 3);
        double airBeneath = getAirBeneathFeet(scanDepth);

        if (!autoClutchActive && !autoClutchLandedGuard && !mc.thePlayer.onGround) {
            // 激活条件多重保险：
            //   0. 上升期 (motionY > 0.01) 绝不激活 —— 跳起来的瞬间，哪怕空距够大也还没在下落
            //   1. fallDistance > 0.5      — 已经进入下落阶段（不是刚离地的瞬间）
            //   2. 实际空距 >= minFall - 0.5 — 脚下真的有 minFall 左右的落差，不是跳起来离地面一两格
            //   3. willFallFar 预测掉落会超过阈值 — 将来落地的位置会伤害玩家
            //   4. (附加) 浅空距保护：fallDistance 还很小时(<1.5)，要求实际空距至少 minFall+1，避免"离地3格就乱放"
            boolean rising = mc.thePlayer.motionY > 0.01;
            boolean shallowFall = mc.thePlayer.fallDistance < 1.5F
                    && airBeneath < Math.max(minFall + 1.0, 3.5);
            if (!rising
                    && !shallowFall
                    && mc.thePlayer.fallDistance > 0.5F
                    && airBeneath >= Math.max(1.5, minFall - 0.5)
                    && willFallFar(minFall)) {
                autoClutchActive = true;
                autoClutchChecking = false;
            }
        }

        if (autoClutchChecking && !autoClutchActive && !autoClutchLandedGuard) {
            if (autoClutchCheckCounter == 0 || autoClutchCheckCounter % 3 == 0) {
                if (airBeneath >= Math.max(1.5, minFall - 0.5) && willFallFar(minFall)) {
                    autoClutchActive = true;
                }
            }
            autoClutchCheckCounter++;
        }

        if (autoClutchLandedGuard) {
            boolean expired = ticksExisted - autoClutchLandedTick >= 10;
            boolean jumped = mc.gameSettings.keyBindJump.isKeyDown();
            boolean airborneUp = !mc.thePlayer.onGround && mc.thePlayer.motionY > 0;
            if (expired || jumped || airborneUp) {
                autoClutchActive = false;
                autoClutchChecking = false;
                autoClutchLandedGuard = false;
            }
        }

        if (autoClutchActive && mc.thePlayer.onGround && mc.thePlayer.hurtTime < mc.thePlayer.maxHurtTime - 2) {
            if (!autoClutchLandedGuard) {
                autoClutchLandedGuard = true;
                autoClutchLandedTick = ticksExisted;
                if (!willFallSoon()) {
                    autoClutchActive = false;
                    autoClutchChecking = false;
                    autoClutchLandedGuard = false;
                }
            }
        }

        if (!autoClutchActive && !autoClutchLandedGuard && mc.thePlayer.onGround && mc.thePlayer.hurtTime == 0) {
            autoClutchChecking = false;
            autoClutchCheckCounter = 0;
        }

        // ---------- Auto SCAF Logic ----------
        if (autoScaf.getValue()) {
            Module scaf = Elara.moduleManager.getModule(elara.module.world.Scaffold.class);
            if (scaf != null) {
                // 只有真的快要掉下去才开 Scaffold：
                //   - 不在地面
                //   - 脚下实际空距至少有 minFall-0.5（默认3.0→2.5），避免2格高乱开
                //   - autoClutchActive 或 willFallFar 成立
                boolean realFallHazard = airBeneath >= Math.max(2.0, minFall - 0.5);
                boolean needScaf = !mc.thePlayer.onGround
                        && realFallHazard
                        && (autoClutchActive || willFallFar(minFall));
                if (needScaf && !scaf.isEnabled() && !scafToggledByClutch) {
                    scaf.setEnabled(true);
                    scafToggledByClutch = true;
                }
                boolean shouldTurnOff = scafToggledByClutch && scaf.isEnabled()
                        && mc.thePlayer.onGround
                        && mc.thePlayer.hurtTime == 0
                        && !willFallFar(minFall);
                if (shouldTurnOff) {
                    scaf.setEnabled(false);
                    scafToggledByClutch = false;
                }
            }
        } else if (scafToggledByClutch) {
            Module scaf = Elara.moduleManager.getModule(elara.module.world.Scaffold.class);
            if (scaf != null && scaf.isEnabled()) {
                scaf.setEnabled(false);
            }
            scafToggledByClutch = false;
        }
    }

    private void enablePlacing() {
        if (placing) return;
        placing = true;
        if (!slotWasSwapped) prevSlot = mc.thePlayer.inventory.currentItem;
    }

    private void disablePlacing(boolean forceRestore) {
        if (!placing && !forceRestore) return;

        placing = false;
        plannedSlot = -1;

        if ((forceRestore || !hasAim) && slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            slotWasSwapped = false;
        }
        if (forceRestore) {
            prevSlot = -1;
            restoreInputs();
        }
    }

    private void clearAim(boolean allowSnapback) {
        if (slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            slotWasSwapped = false;
        }
        targetHitPos = null;
        targetSide = null;
        lastPlaced = null;
        clutchBlocksPlaced = 0;
        if (allowSnapback && hasAim) resetting = true;
        hasAim = false;
        prevSlot = -1;
    }

    private void restoreInputs() {
        if (mc.currentScreen == null) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), org.lwjgl.input.Mouse.isButtonDown(0));
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), org.lwjgl.input.Mouse.isButtonDown(1));
        }
    }

    private boolean willFallFar(double minFall) {
        double startY = mc.thePlayer.posY;
        PredictionState prediction = PredictionState.fromPlayer();
        for (int t = 0; t < 60; t++) {
            prediction.tick(false);
            if (prediction.onGround) {
                return false;
            }
            double fall = startY - prediction.posY;
            if (fall > minFall) {
                return true;
            }
        }
        return false;
    }

    private boolean willFallSoon() {
        PredictionState prediction = PredictionState.fromPlayer();
        for (int t = 0; t < 10; t++) {
            prediction.tick(true);
            if (!prediction.onGround && prediction.motionY < 0) {
                return true;
            }
        }
        return false;
    }

    private AimResult clutchAim() {
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);

        Vec3 futurePos = playerPos;
        if (simulateFuturePosition.getValue()) {
            PredictionState prediction = PredictionState.fromPlayer();
            for (int t = 0; t < 20; t++) {
                prediction.tick(false);
                if (prediction.posY < playerPos.yCoord - 2 || prediction.onGround) break;
            }
            futurePos = prediction.getPos();
        }

        int feetX = MathHelper.floor_double(playerPos.xCoord);
        int feetZ = MathHelper.floor_double(playerPos.zCoord);
        int feetY = MathHelper.floor_double(playerPos.yCoord);
        int minX = feetX - 5;
        int maxX = feetX + 4;
        int minZ = feetZ - 5;
        int maxZ = feetZ + 4;
        int maxY = feetY - 1;
        int minY = feetY - 4;

        ArrayList<BlockCandidate> candidates = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (canPlaceThrough(pos)) continue;

                    double currentDist = distToPointAABB(playerPos, pos);
                    double futureDist = distToPointAABB(futurePos, pos);
                    double score = simulateFuturePosition.getValue() ? (currentDist * 0.3 + futureDist * 0.7) : currentDist;
                    if (pos.equals(lastPlaced)) score *= 0.95;
                    candidates.add(new BlockCandidate(score, pos));
                }
            }
        }

        candidates.sort((a, b) -> Double.compare(a.score, b.score));

        ItemStack held = plannedSlot >= 0 && plannedSlot <= 8 ? mc.thePlayer.inventory.mainInventory[plannedSlot] : null;
        for (BlockCandidate candidate : candidates) {
            boolean underPlayer = isBlockUnderPlayer(candidate.pos, playerPos);
            AimResult result = getBestRotationsToBlock(held, candidate.pos, eye, reach.getValue(), underPlayer);
            if (result != null) return result;
        }

        return null;
    }

    private boolean isBlockUnderPlayer(BlockPos blockPos, Vec3 pos) {
        if (blockPos.getY() >= MathHelper.floor_double(pos.yCoord)) return false;
        for (double[] corner : CORNERS) {
            int cx = MathHelper.floor_double(pos.xCoord + corner[0]);
            int cz = MathHelper.floor_double(pos.zCoord + corner[1]);
            if (blockPos.getX() == cx && blockPos.getZ() == cz) return true;
        }
        return false;
    }

    private AimResult getBestRotationsToBlock(ItemStack held, BlockPos targetCell, Vec3 eye, double reachVal, boolean underPlayer) {
        double inset = 0.05;
        double step = 0.2;
        double jitter = step * 0.1;
        boolean faceSouth = Math.abs(eye.zCoord - (targetCell.getZ() + 1)) < Math.abs(eye.zCoord - targetCell.getZ());
        boolean faceEast = Math.abs(eye.xCoord - (targetCell.getX() + 1)) < Math.abs(eye.xCoord - targetCell.getX());
        float baseYaw = normYaw(currentYaw);
        float basePitch = currentPitch;
        int n = (int) Math.round(1.0 / step);

        ArrayList<RotationCandidate> candidates = new ArrayList<>();
        candidates.add(new RotationCandidate(0, baseYaw, basePitch));

        for (int row = 0; row <= n; row++) {
            double v = clamp01(row * step + randomRange(-jitter, jitter));
            for (int col = 0; col <= n; col++) {
                double u = clamp01(col * step + randomRange(-jitter, jitter));

                if (underPlayer) {
                    float[] rV = getRotationsWrapped(eye, targetCell.getX() + u, targetCell.getY() + 1 - inset, targetCell.getZ() + v);
                    double costV = Math.abs(wrapYawDelta(baseYaw, rV[0])) + Math.abs(rV[1] - basePitch);
                    candidates.add(new RotationCandidate(costV, rV[0], rV[1]));
                }

                float[] rZ = getRotationsWrapped(eye, targetCell.getX() + u, targetCell.getY() + v, faceSouth ? targetCell.getZ() + 1 - inset : targetCell.getZ() + inset);
                double costZ = Math.abs(wrapYawDelta(baseYaw, rZ[0])) + Math.abs(rZ[1] - basePitch);
                candidates.add(new RotationCandidate(costZ, rZ[0], rZ[1]));

                float[] rX = getRotationsWrapped(eye, faceEast ? targetCell.getX() + 1 - inset : targetCell.getX() + inset, targetCell.getY() + v, targetCell.getZ() + u);
                double costX = Math.abs(wrapYawDelta(baseYaw, rX[0])) + Math.abs(rX[1] - basePitch);
                candidates.add(new RotationCandidate(costX, rX[0], rX[1]));
            }
        }

        candidates.sort((a, b) -> Double.compare(a.cost, b.cost));

        for (RotationCandidate candidate : candidates) {
            float yaw = unwrapYaw(candidate.yaw, currentYaw);
            MovingObjectPosition ray = RotationUtil.rayTrace(yaw, candidate.pitch, reachVal, 1.0F);
            if (ray == null) continue;

            EnumFacing face = ray.sideHit;
            if (face == EnumFacing.DOWN) continue;
            if (face == EnumFacing.UP && !underPlayer) continue;
            if (!targetCell.equals(ray.getBlockPos())) continue;
            if (!canPlaceBlockOnSide(held, ray.getBlockPos(), face)) continue;

            return new AimResult(ray, yaw, candidate.pitch);
        }

        return null;
    }

    private int pickBlockSlot() {
        boolean playingBedwars = isBedwars();
        if (!playingBedwars) {
            int current = mc.thePlayer.inventory.currentItem;
            if (isBlockSlot(current)) return current;

            for (int slot = 8; slot >= 0; --slot) {
                if (isBlockSlot(slot)) return slot;
            }
            return -1;
        }

        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int slot = 8; slot >= 0; --slot) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack == null || stack.stackSize == 0 || !(stack.getItem() instanceof ItemBlock)) continue;

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            ResourceLocation id = Block.blockRegistry.getNameForObject(block);
            if (id == null) continue;

            Integer score = BLOCK_SCORE.get(id.getResourcePath());
            if (score == null) continue;

            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private boolean isBedwars() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        BlockPos pos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos bedPos = pos.down();
        Block blockBelow = mc.theWorld.getBlockState(bedPos).getBlock();
        return blockBelow instanceof net.minecraft.block.BlockBed;
    }

    private boolean isBlockSlot(int slot) {
        if (slot < 0 || slot > 8) return false;
        ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock;
    }

    private void equipPlannedSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        if (plannedSlot != -1 && plannedSlot != current) {
            mc.thePlayer.inventory.currentItem = plannedSlot;
            slotWasSwapped = true;
        }
    }

    private float[] getRotationsSmoothed(float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean snapback) {
        float curYaw = currentYaw;
        float curPitch = currentPitch;
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;

        if (Math.abs(deltaYaw) < 0.1f) curYaw = targetYaw;
        if (Math.abs(deltaPitch) < 0.1f) curPitch = targetPitch;
        if (curYaw == targetYaw && curPitch == targetPitch) {
            return new float[]{curYaw, MathHelper.clamp_float(curPitch, -90.0F, 90.0F)};
        }

        float maxStep = snapback ? snapbackSpeed.getValue() : speed.getValue();
        float factor = 1.0F - (float) randomRange(0.0, 0.2);
        maxStep *= factor;

        float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
        if (totalDelta <= maxStep) {
            curYaw = targetYaw;
            curPitch = targetPitch;
        } else if (maxStep > 0) {
            float scale = maxStep / totalDelta;
            curYaw += deltaYaw * scale;
            curPitch += deltaPitch * scale;
        }

        return new float[]{curYaw, MathHelper.clamp_float(curPitch, -90.0F, 90.0F)};
    }

    private boolean canPlaceThrough(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        Material material = block.getMaterial();
        return material == Material.air || material == Material.water || material == Material.lava || block == Blocks.fire;
    }

    /**
     * 检测玩家脚下 within 格范围内（沿 4 个角柱）是否存在非空气方块支撑。
     * 用于上升初期判定是否安全，避免跳起来也触发 clutch 放置。
     */
    private boolean hasSolidGroundBeneath(double within) {
        int feetY = MathHelper.floor_double(mc.thePlayer.posY);
        int maxScan = Math.max(1, (int) Math.ceil(within));
        for (int dy = 1; dy <= maxScan; dy++) {
            int checkY = feetY - dy;
            for (double[] corner : CORNERS) {
                int cx = MathHelper.floor_double(mc.thePlayer.posX + corner[0]);
                int cz = MathHelper.floor_double(mc.thePlayer.posZ + corner[1]);
                BlockPos pos = new BlockPos(cx, checkY, cz);
                if (!canPlaceThrough(pos)) return true;
            }
        }
        return false;
    }

    /**
     * 玩家脚下实际空距（沿 4 角柱取最小），向下扫描 maxScan 格。
     * 角柱下任一方块触地即视为该柱的空距。用于"几格高掉下去"的真实判定。
     */
    private double getAirBeneathFeet(int maxScan) {
        double feetY = mc.thePlayer.posY; // 玩家脚底板 Y
        double minAir = maxScan + 1.0;
        for (double[] corner : CORNERS) {
            int cx = MathHelper.floor_double(mc.thePlayer.posX + corner[0]);
            int cz = MathHelper.floor_double(mc.thePlayer.posZ + corner[1]);
            int feetBlock = MathHelper.floor_double(feetY - 0.001);
            boolean found = false;
            for (int dy = 0; dy < maxScan; dy++) {
                BlockPos p = new BlockPos(cx, feetBlock - dy, cz);
                if (!canPlaceThrough(p)) {
                    // 从脚底板到该方块顶面的距离
                    double topY = (feetBlock - dy) + 1.0;
                    double d = feetY - topY;
                    if (d < minAir) minAir = d;
                    found = true;
                    break;
                }
            }
            if (!found) {
                // 此柱下 maxScan 格内无支撑，取最大值
                if (maxScan < minAir) minAir = maxScan;
            }
        }
        return minAir;
    }

    private boolean canPlaceBlockOnSide(ItemStack held, BlockPos pos, EnumFacing side) {
        if (held == null || !(held.getItem() instanceof ItemBlock)) return false;
        if (!canPlaceThrough(pos.offset(side))) return false;
        return BlockUtil.isReplaceable(pos.offset(side));
    }

    private static double distToPointAABB(Vec3 point, BlockPos pos) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        double closestX = MathHelper.clamp_double(point.xCoord, minX, maxX);
        double closestY = MathHelper.clamp_double(point.yCoord, minY, maxY);
        double closestZ = MathHelper.clamp_double(point.zCoord, minZ, maxZ);

        double dx = point.xCoord - closestX;
        double dy = point.yCoord - closestY;
        double dz = point.zCoord - closestZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double randomRange(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private static float normYaw(float yaw) {
        yaw = ((yaw % 360.0F) + 360.0F) % 360.0F;
        return yaw > 180.0F ? yaw - 360.0F : yaw;
    }

    private static float wrapYawDelta(float base, float target) {
        return MathHelper.wrapAngleTo180_float(target - base);
    }

    private static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + MathHelper.wrapAngleTo180_float(yaw - prevYaw);
    }

    private static float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord;
        double dy = ty - eye.yCoord;
        double dz = tz - eye.zCoord;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDistance));
        return new float[]{normYaw(yaw), MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    private static class BlockCandidate {
        final double score;
        final BlockPos pos;

        BlockCandidate(double score, BlockPos pos) {
            this.score = score;
            this.pos = pos;
        }
    }

    private static class RotationCandidate {
        final double cost;
        final float yaw;
        final float pitch;

        RotationCandidate(double cost, float yaw, float pitch) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class AimResult {
        final MovingObjectPosition ray;
        final float yaw;
        final float pitch;

        AimResult(MovingObjectPosition ray, float yaw, float pitch) {
            this.ray = ray;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class PredictionState {
        private AxisAlignedBB box;
        private double motionX;
        private double motionY;
        private double motionZ;
        private double posY;
        private boolean onGround;

        static PredictionState fromPlayer() {
            PredictionState state = new PredictionState();
            state.box = mc.thePlayer.getEntityBoundingBox();
            state.motionX = mc.thePlayer.motionX;
            state.motionY = mc.thePlayer.motionY;
            state.motionZ = mc.thePlayer.motionZ;
            state.posY = mc.thePlayer.posY;
            state.onGround = mc.thePlayer.onGround;
            return state;
        }

        Vec3 getPos() {
            return new Vec3((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
        }

        void tick(boolean stopHorizontal) {
            if (stopHorizontal) {
                motionX = 0.0;
                motionZ = 0.0;
            }

            motionY -= 0.08;
            move(motionX, motionY, motionZ);
            motionY *= 0.9800000190734863;
            motionX *= 0.91;
            motionZ *= 0.91;
        }

        private void move(double x, double y, double z) {
            double originalX = x;
            double originalY = y;
            double originalZ = z;

            List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.addCoord(x, y, z));
            for (AxisAlignedBB collision : collisions) {
                y = collision.calculateYOffset(box, y);
            }
            box = box.offset(0.0, y, 0.0);

            for (AxisAlignedBB collision : collisions) {
                x = collision.calculateXOffset(box, x);
            }
            box = box.offset(x, 0.0, 0.0);

            for (AxisAlignedBB collision : collisions) {
                z = collision.calculateZOffset(box, z);
            }
            box = box.offset(0.0, 0.0, z);

            onGround = originalY != y && originalY < 0.0;
            posY = box.minY;

            if (originalX != x) motionX = 0.0;
            if (originalY != y) motionY = 0.0;
            if (originalZ != z) motionZ = 0.0;
        }
    }
}
