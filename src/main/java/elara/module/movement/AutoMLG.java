package elara.module.movement;

import java.util.Random;
import java.util.function.Predicate;
import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorKeyBinding;
import elara.mixin.IAccessorMinecraft;
import elara.mixin.IAccessorPlayerControllerMP;
import elara.module.Module;
import elara.property.properties.BooleanProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.IntProperty;
import elara.module.world.Scaffold;
import elara.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoMLG extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    public final BooleanProperty lethalOnly = new BooleanProperty("lethal-fall", false);
    public final FloatProperty minDamage = new FloatProperty("min-damage", 6.0F, 1.0F, 10.0F, () -> !lethalOnly.getValue());
    public final BooleanProperty water = new BooleanProperty("water", true);
    public final BooleanProperty hayBale = new BooleanProperty("hay-bale", true);
    public final BooleanProperty cobweb = new BooleanProperty("cobweb", true);
    public final IntProperty waterPrio = new IntProperty("water-priority", 1, 1, 3, () -> water.getValue());
    public final IntProperty hayPrio = new IntProperty("hay-priority", 2, 1, 3, () -> hayBale.getValue());
    public final IntProperty cobPrio = new IntProperty("cobweb-priority", 3, 1, 3, () -> cobweb.getValue());
    public final BooleanProperty pickupWater = new BooleanProperty("pickup-water-buggy", true, () -> water.getValue());
    public final BooleanProperty checkInv = new BooleanProperty("check-inventory", true);
    public final BooleanProperty legitMode = new BooleanProperty("legit-mode", true, () -> checkInv.getValue());
    public final IntProperty clickDelayMin = new IntProperty("click-delay-min", 75, 0, 1000, () -> checkInv.getValue() && legitMode.getValue());
    public final IntProperty clickDelayMax = new IntProperty("click-delay-max", 100, 0, 1000, () -> checkInv.getValue() && legitMode.getValue());

    private int savedSlot = -1;
    private boolean didMLG = false;
    private boolean slotPrepped = false;
    private boolean mlgPlaced = false;
    private int slotSwitchTick = 0;
    private BlockPos waterPos = null;
    private int lockedType = -1;
    private int pickupTick = 0;
    private int invState = 0;
    private int invSlot = -1;
    private int invTargetHotbar = -1;
    private long invActionTime = 0L;
    private long invNextDelay = 0L;

    public AutoMLG() {
        super("AutoMLG", false);
    }

    private float predictedFallDistance() {
        double motionY = mc.thePlayer.motionY;
        double posY = mc.thePlayer.posY;
        float fallen = mc.thePlayer.fallDistance;

        for (int i = 0; i < 200; ++i) {
            motionY -= 0.08;
            motionY *= 0.98;
            if (motionY < 0.0) {
                fallen += (float) (-motionY);
            }
            double nextY = posY + motionY;
            AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox().offset(0.0, nextY - mc.thePlayer.posY, 0.0);
            if (!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, bb).isEmpty()) {
                break;
            }
            posY = nextY;
        }
        return fallen;
    }

    private float calculateFallDamage(float totalFall) {
        if (totalFall <= 3.0F) return 0.0F;
        float damage = totalFall - 3.0F;
        if (mc.thePlayer.isPotionActive(Potion.jump)) {
            int level = mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1;
            damage -= (float) (level * 2);
        }
        ItemStack boots = mc.thePlayer.inventory.armorItemInSlot(0);
        if (boots != null) {
            int ff = EnchantmentHelper.getEnchantmentLevel(Enchantment.featherFalling.effectId, boots);
            if (ff > 0) {
                damage -= (float) (ff * 2);
            }
        }
        return Math.max(0.0F, damage);
    }

    private boolean shouldActivate(float totalFall) {
        if (!mc.thePlayer.onGround && mc.thePlayer.motionY < 0.0) {
            float dmg = calculateFallDamage(totalFall);
            if (dmg <= 0.0F) {
                return false;
            } else if (lethalOnly.getValue()) {
                return dmg >= mc.thePlayer.getHealth();
            } else {
                return dmg >= minDamage.getValue();
            }
        } else {
            return false;
        }
    }

    private float[] calcRotationToBlock(MovingObjectPosition mop) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double topY = (double) mop.getBlockPos().getY() + 1.0;
        double cx = Math.max(mop.getBlockPos().getX(),
                Math.min(mop.getBlockPos().getX() + 1.0, eyes.xCoord));
        double cz = Math.max(mop.getBlockPos().getZ(),
                Math.min(mop.getBlockPos().getZ() + 1.0, eyes.zCoord));
        double dx = cx - eyes.xCoord;
        double dy = topY - eyes.yCoord;
        double dz = cz - eyes.zCoord;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = Math.max(80.0F, Math.min(90.0F,
                (float) (-Math.atan2(dy, h) * 180.0 / Math.PI)));
        return new float[]{yaw, pitch};
    }

    private boolean isWaterBucket(ItemStack s) {
        if (s == null) {
            return false;
        }
        Item item = s.getItem();
        if (item == Items.water_bucket) {
            return true;
        }
        String name = item.getClass().getSimpleName().toLowerCase();
        return name.contains("bucket") && !name.contains("empty")
                && item != Items.lava_bucket && item != Items.milk_bucket;
    }

    private boolean isHayBale(ItemStack s) {
        return s != null && s.getItem() instanceof ItemBlock
                && ((ItemBlock) s.getItem()).getBlock() == Blocks.hay_block;
    }

    private boolean isCobweb(ItemStack s) {
        return s != null && s.getItem() instanceof ItemBlock
                && ((ItemBlock) s.getItem()).getBlock() == Blocks.web;
    }

    private Predicate<ItemStack> predicateForType(int type) {
        switch (type) {
            case 0: return this::isWaterBucket;
            case 1: return this::isHayBale;
            case 2: return this::isCobweb;
            default: return (s) -> false;
        }
    }

    private int findHotbar(Predicate<ItemStack> test) {
        for (int i = 0; i < 9; ++i) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && test.test(s)) {
                return i;
            }
        }
        return -1;
    }

    private int findInventory(Predicate<ItemStack> test) {
        for (int i = 9; i < 36; ++i) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && test.test(s)) {
                return i;
            }
        }
        return -1;
    }

    private int findHotbarItem(Item target) {
        for (int i = 0; i < 9; ++i) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() == target) {
                return i;
            }
        }
        return -1;
    }

    private int findInventoryItem(Item target) {
        for (int i = 9; i < 36; ++i) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() == target) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; ++i) {
            if (i != savedSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) {
                return i;
            }
        }
        for (int i = 0; i < 9; ++i) {
            if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
                return i;
            }
        }
        return savedSlot == 8 ? 7 : 8;
    }

    private int[] findBestMLGItem() {
        int[][] entries = new int[3][];
        int count = 0;

        if (water.getValue()) {
            int h = findHotbar(this::isWaterBucket);
            int inv = h == -1 && checkInv.getValue() ? findInventory(this::isWaterBucket) : -1;
            if (h != -1 || inv != -1) {
                entries[count++] = new int[]{waterPrio.getValue(), 0, h, inv};
            }
        }
        if (hayBale.getValue()) {
            int h = findHotbar(this::isHayBale);
            int inv = h == -1 && checkInv.getValue() ? findInventory(this::isHayBale) : -1;
            if (h != -1 || inv != -1) {
                entries[count++] = new int[]{hayPrio.getValue(), 1, h, inv};
            }
        }
        if (cobweb.getValue()) {
            int h = findHotbar(this::isCobweb);
            int inv = h == -1 && checkInv.getValue() ? findInventory(this::isCobweb) : -1;
            if (h != -1 || inv != -1) {
                entries[count++] = new int[]{cobPrio.getValue(), 2, h, inv};
            }
        }

        if (count == 0) {
            return null;
        }

        // sort by priority
        for (int i = 0; i < count - 1; ++i) {
            for (int j = i + 1; j < count; ++j) {
                if (entries[j][0] < entries[i][0]) {
                    int[] tmp = entries[i];
                    entries[i] = entries[j];
                    entries[j] = tmp;
                }
            }
        }

        // prefer hotbar over inventory
        for (int i = 0; i < count; ++i) {
            if (entries[i][2] != -1) {
                return new int[]{entries[i][1], entries[i][2], -1};
            }
        }
        for (int i = 0; i < count; ++i) {
            if (entries[i][3] != -1) {
                return new int[]{entries[i][1], -1, entries[i][3]};
            }
        }
        return null;
    }

    private void sendSlotPacket(int slot) {
        ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }

    private long randomDelay() {
        int lo = clickDelayMin.getValue();
        int hi = clickDelayMax.getValue();
        return lo >= hi ? lo : lo + (long) (random.nextFloat() * (hi - lo));
    }

    private void tickLegitInv() {
        if (invState == 0) return;
        long now = System.currentTimeMillis();
        if (invState == 1) {
            if (now - invActionTime >= invNextDelay) {
                mc.playerController.windowClick(0, invSlot, invTargetHotbar, 2, mc.thePlayer);
                invState = 2;
                invActionTime = now;
                invNextDelay = randomDelay();
            }
        } else if (invState == 2) {
            if (now - invActionTime < invNextDelay) {
                return;
            }
            ((IAccessorKeyBinding) mc.gameSettings.keyBindInventory).setPressed(false);
            mc.thePlayer.closeScreen();
            invState = 0;
        }
    }

    private void startLegitInv(int slot) {
        if (invState == 0) {
            invSlot = slot;
            invTargetHotbar = findEmptyHotbarSlot();
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            invState = 1;
            invActionTime = System.currentTimeMillis();
            invNextDelay = randomDelay();
        }
    }

    private void tryPickupWater() {
        if (waterPos == null) return;
        Block block = mc.theWorld.getBlockState(waterPos).getBlock();
        if (block != Blocks.water && block != Blocks.flowing_water) {
            waterPos = null;
            return;
        }

        int emptySlot = findHotbarItem(Items.bucket);
        if (emptySlot == -1 && checkInv.getValue()) {
            int inv = findInventoryItem(Items.bucket);
            if (inv != -1) {
                int target = findEmptyHotbarSlot();
                mc.playerController.windowClick(0, inv, target, 2, mc.thePlayer);
                emptySlot = target;
            }
        }

        if (emptySlot != -1) {
            sendSlotPacket(emptySlot);
            mc.thePlayer.inventory.currentItem = emptySlot;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(emptySlot);
            if (stack != null) {
                float prevYaw = mc.thePlayer.rotationYaw;
                float prevPitch = mc.thePlayer.rotationPitch;
                Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
                double dx = waterPos.getX() + 0.5 - eyes.xCoord;
                double dy = waterPos.getY() + 0.5 - eyes.yCoord;
                double dz = waterPos.getZ() + 0.5 - eyes.zCoord;
                double hDist = Math.sqrt(dx * dx + dz * dz);
                float pickYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                float pickPitch = (float) (-Math.atan2(dy, hDist) * 180.0 / Math.PI);
                mc.thePlayer.rotationYaw = pickYaw;
                mc.thePlayer.rotationPitch = pickPitch;
                mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                        waterPos, EnumFacing.UP,
                        new Vec3(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5));
                mc.thePlayer.rotationYaw = prevYaw;
                mc.thePlayer.rotationPitch = prevPitch;
                waterPos = null;
            }
        }
    }

    private void doPlace(int slot, float yaw, float pitch, MovingObjectPosition mop) {
        if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) return;

        float pYaw = mc.thePlayer.rotationYaw;
        float pPitch = mc.thePlayer.rotationPitch;
        float ppYaw = mc.thePlayer.prevRotationYaw;
        float ppPitch = mc.thePlayer.prevRotationPitch;

        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;

        MovingObjectPosition prevMop = mc.objectMouseOver;
        mc.objectMouseOver = mop;
        ((IAccessorMinecraft) mc).rightClickMouse();

        if (isWaterBucket(mc.thePlayer.inventory.getStackInSlot(slot))) {
            waterPos = mop.getBlockPos().offset(mop.sideHit);
            pickupTick = 3;
        }

        mc.objectMouseOver = prevMop;
        mc.thePlayer.rotationYaw = pYaw;
        mc.thePlayer.rotationPitch = pPitch;
        mc.thePlayer.prevRotationYaw = ppYaw;
        mc.thePlayer.prevRotationPitch = ppPitch;
    }

    private void resetState() {
        savedSlot = -1;
        didMLG = false;
        slotPrepped = false;
        mlgPlaced = false;
        slotSwitchTick = 0;
        lockedType = -1;
        pickupTick = 0;
        invState = 0;
        invSlot = -1;
        invTargetHotbar = -1;
        invActionTime = 0L;
        invNextDelay = 0L;
    }

    @EventTarget(1)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (event.getType() != EventType.PRE) return;

        Scaffold scaffold = (Scaffold) Elara.moduleManager.modules.get(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) return;

        if (mc.currentScreen != null && invState == 0) return;

        tickLegitInv();

        if (mc.thePlayer.onGround) {
            if (didMLG) {
                if (pickupWater.getValue() && waterPos != null) {
                    if (pickupTick > 0) {
                        --pickupTick;
                        return;
                    }
                    tryPickupWater();
                }
                if (savedSlot != -1) {
                    sendSlotPacket(savedSlot);
                    mc.thePlayer.inventory.currentItem = savedSlot;
                }
                resetState();
            } else {
                waterPos = null;
            }
            return;
        }

        float totalFall = predictedFallDistance();
        if (!shouldActivate(totalFall)) return;

        int mlgSlot = -1;
        int needsInv = -1;

        if (lockedType != -1) {
            Predicate<ItemStack> pred = predicateForType(lockedType);
            mlgSlot = findHotbar(pred);
            if (mlgSlot == -1 && checkInv.getValue()) {
                needsInv = findInventory(pred);
            }
        } else {
            int[] best = findBestMLGItem();
            if (best == null) return;
            lockedType = best[0];
            mlgSlot = best[1];
            needsInv = best[2];
        }

        if (mlgSlot != -1 || needsInv != -1) {
            if (mlgSlot == -1 && needsInv != -1) {
                if (legitMode.getValue()) {
                    startLegitInv(needsInv);
                    mlgSlot = findHotbar(predicateForType(lockedType));
                    if (mlgSlot == -1) return;
                } else {
                    int hotbarSlot = findEmptyHotbarSlot();
                    mc.playerController.windowClick(0, needsInv, hotbarSlot, 2, mc.thePlayer);
                    mlgSlot = hotbarSlot;
                }
            }

            if (mc.thePlayer.fallDistance >= totalFall / 2.0F && !slotPrepped) {
                if (savedSlot == -1) {
                    savedSlot = mc.thePlayer.inventory.currentItem;
                }
                sendSlotPacket(mlgSlot);
                mc.thePlayer.inventory.currentItem = mlgSlot;
                slotPrepped = true;
            }

            MovingObjectPosition mop = RotationUtil.rayTrace(event.getYaw(), 90.0F, 4.5, 1.0F);
            if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
                float[] rot = calcRotationToBlock(mop);
                float yaw = rot[0];
                float pitch = rot[1];

                if (!didMLG) {
                    if (savedSlot == -1) {
                        savedSlot = mc.thePlayer.inventory.currentItem;
                    }
                    didMLG = true;
                }

                event.setRotation(yaw, pitch, 10);

                if (!mlgPlaced) {
                    if (slotSwitchTick == 0) {
                        sendSlotPacket(mlgSlot);
                        mc.thePlayer.inventory.currentItem = mlgSlot;
                        slotSwitchTick = 1;
                    } else {
                        doPlace(mlgSlot, yaw, pitch, mop);
                        mlgPlaced = true;
                        slotSwitchTick = 0;
                    }
                } else {
                    BlockPos above = mop.getBlockPos().offset(mop.sideHit);
                    Block there = mc.theWorld.getBlockState(above).getBlock();
                    boolean stillThere = there == Blocks.web || there == Blocks.hay_block
                            || there == Blocks.water || there == Blocks.flowing_water;
                    if (!stillThere && waterPos == null) {
                        mlgPlaced = false;
                    }
                }
            }
        }
    }

    public void onDisabled() {
        if (mc.thePlayer != null && savedSlot != -1) {
            sendSlotPacket(savedSlot);
            mc.thePlayer.inventory.currentItem = savedSlot;
        }
        waterPos = null;
        resetState();
    }
}