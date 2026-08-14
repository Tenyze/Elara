package elara.module.utility;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.UpdateEvent;
import elara.events.WindowClickEvent;
import elara.mixin.IAccessorItemSword;
import elara.module.Module;
import elara.util.ChatUtil;
import elara.util.ItemUtil;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ChestStealer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    private int clickDelay = 0;
    private int oDelay = 0;
    private boolean inChest = false;
    private boolean warnedFull = false;

    // 搜索顺序状态
    private List<Integer> stealOrder = new ArrayList<>();
    private int currentStealIndex = 0;
    private boolean orderGenerated = false;

    // ---------- 设置项 ----------
    public final ModeProperty selectionMode = new ModeProperty("SelectionMode", 0, new String[]{"Distance", "Index", "Random"});
    public final IntProperty minDelay = new IntProperty("min-delay", 1, 0, 20);
    public final IntProperty maxDelay = new IntProperty("max-delay", 2, 0, 20);
    public final IntProperty openDelay = new IntProperty("open-delay", 1, 0, 20);
    public final BooleanProperty autoClose = new BooleanProperty("auto-close", false);
    public final BooleanProperty nameCheck = new BooleanProperty("name-check", true);
    public final BooleanProperty HypixelMode = new BooleanProperty("hypixel-mode", false);
    public final BooleanProperty skipTrash = new BooleanProperty("skip-trash", true);
    public final BooleanProperty moreArmor = new BooleanProperty("more-armor", false);
    public final BooleanProperty moreSword = new BooleanProperty("more-sword", false);

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private boolean isMoreArmor(ItemStack itemStack) {
        if (itemStack == null) return false;
        if (!this.moreArmor.getValue()) return false;
        if (!(itemStack.getItem() instanceof ItemArmor)) return false;
        ItemArmor.ArmorMaterial armorMaterial = ((ItemArmor) itemStack.getItem()).getArmorMaterial();
        if (armorMaterial == ItemArmor.ArmorMaterial.DIAMOND) return true;
        return armorMaterial == ItemArmor.ArmorMaterial.IRON && itemStack.isItemEnchanted();
    }

    private boolean isMoreSword(ItemStack itemStack) {
        if (itemStack == null) return false;
        if (!this.moreSword.getValue()) return false;
        if (!(itemStack.getItem() instanceof ItemSword)) return false;
        Item.ToolMaterial swordMaterial = ((IAccessorItemSword) itemStack.getItem()).getMaterial();
        if (swordMaterial == Item.ToolMaterial.EMERALD) return true;
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack) != 0) return true;
        return swordMaterial == Item.ToolMaterial.IRON && itemStack.isItemEnchanted();
    }

    private boolean isInvManagerRequire(ItemStack itemStack) {
        if (itemStack == null) return false;
        elara.module.utility.InvManager invManager = (elara.module.utility.InvManager) Elara.moduleManager.modules.get(elara.module.utility.InvManager.class);
        if (ItemUtil.ItemType.Block.contains(itemStack)) {
            return !invManager.isEnabled() || ItemUtil.findInventorySlot(ItemUtil.ItemType.Block) < invManager.blocks.getValue();
        }
        if (ItemUtil.ItemType.Projectile.contains(itemStack)) {
            return !invManager.isEnabled() || ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile) < invManager.projectiles.getValue();
        }
        if (ItemUtil.ItemType.FishRod.contains(itemStack)) {
            return ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile) == 0;
        }
        if (ItemUtil.ItemType.Arrow.contains(itemStack)) {
            return !invManager.isEnabled() || ItemUtil.findInventorySlot(ItemUtil.ItemType.Arrow) < invManager.arrow.getValue();
        }
        return false;
    }

    /**
     * 判断一个物品是否应该被偷（包含 skipTrash / moreArmor / moreSword / invManager 规则）
     */
    private boolean isValidItem(ItemStack stack) {
        if (stack == null) return false;
        if (!this.skipTrash.getValue()) return true;
        if (!ItemUtil.isNotSpecialItem(stack)) return true;
        if (isMoreArmor(stack)) return true;
        if (isMoreSword(stack)) return true;
        if (isInvManagerRequire(stack)) return true;
        return false;
    }

    private void shiftClick(int windowId, int slotId) {
        mc.playerController.windowClick(windowId, slotId, 0, 1, mc.thePlayer);
    }

    public ChestStealer() {
        super("ChestStealer", false);
        selectionMode.setCategory("Other");
    }

    /**
     * 计算"最佳装备优先"的 slot 列表：
     * - 伤害更高的剑
     * - 保护更高的甲（4个部位）
     * - 效率更高的镐 / 铲 / 斧
     * - 伤害更高的弓
     * 只有当箱子里的物品确实比玩家当前身上/背包里更好时，才加入返回列表。
     */
    private List<Integer> collectPrioritySlots(Container container, IInventory inventory) {
        List<Integer> priority = new ArrayList<>();
        if (!this.skipTrash.getValue()) return priority;

        int bestSword = -1;
        double bestDamage = 0.0;
        int[] bestArmorSlots = new int[]{-1, -1, -1, -1};
        double[] bestArmorProtection = new double[]{0.0, 0.0, 0.0, 0.0};
        int bestPickaxeSlot = -1;
        float bestPickaxeEfficiency = 1.0F;
        int bestShovelSlot = -1;
        float bestShovelEfficiency = 1.0F;
        int bestAxeSlot = -1;
        float bestAxeEfficiency = 1.0F;
        int bestBow = -1;
        double bestBowDamage = 0.0;

        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (container.getSlot(i).getHasStack()) {
                ItemStack stack = container.getSlot(i).getStack();
                Item item = stack.getItem();
                if (item instanceof ItemSword) {
                    double damage = ItemUtil.getAttackBonus(stack);
                    if (bestSword == -1 || damage > bestDamage) {
                        bestSword = i;
                        bestDamage = damage;
                    }
                } else if (item instanceof ItemArmor) {
                    int armorType = ((ItemArmor) item).armorType;
                    double protectionLevel = ItemUtil.getArmorProtection(stack);
                    if (bestArmorSlots[armorType] == -1 || protectionLevel > bestArmorProtection[armorType]) {
                        bestArmorSlots[armorType] = i;
                        bestArmorProtection[armorType] = protectionLevel;
                    }
                } else if (item instanceof ItemPickaxe) {
                    float efficiency = ItemUtil.getToolEfficiency(stack);
                    if (bestPickaxeSlot == -1 || efficiency > bestPickaxeEfficiency) {
                        bestPickaxeSlot = i;
                        bestPickaxeEfficiency = efficiency;
                    }
                } else if (item instanceof ItemSpade) {
                    float efficiency = ItemUtil.getToolEfficiency(stack);
                    if (bestShovelSlot == -1 || efficiency > bestShovelEfficiency) {
                        bestShovelSlot = i;
                        bestShovelEfficiency = efficiency;
                    }
                } else if (item instanceof ItemAxe) {
                    float efficiency = ItemUtil.getToolEfficiency(stack);
                    if (bestAxeSlot == -1 || efficiency > bestAxeEfficiency) {
                        bestAxeSlot = i;
                        bestAxeEfficiency = efficiency;
                    }
                } else if (item instanceof ItemBow) {
                    double damage = ItemUtil.getBowAttackBonus(stack);
                    if (bestBow == -1 || damage > bestBowDamage) {
                        bestBow = i;
                        bestBowDamage = damage;
                    }
                }
            }
        }

        int swordInInventorySlot = ItemUtil.findSwordInInventorySlot(0, true);
        double curSwordDmg = swordInInventorySlot != -1 ? ItemUtil.getAttackBonus(mc.thePlayer.inventory.getStackInSlot(swordInInventorySlot)) : 0.0;
        if (bestSword != -1 && bestDamage > curSwordDmg) priority.add(bestSword);

        for (int i = 0; i < 4; i++) {
            int slot = ItemUtil.findArmorInventorySlot(i, true);
            double protectionLevel = slot != -1 ? ItemUtil.getArmorProtection(mc.thePlayer.inventory.getStackInSlot(slot)) : 0.0;
            if (bestArmorSlots[i] != -1 && bestArmorProtection[i] > protectionLevel) priority.add(bestArmorSlots[i]);
        }

        int pickaxeSlot = ItemUtil.findInventorySlot("pickaxe", 0, true);
        float pickaxeEfficiency = pickaxeSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(pickaxeSlot)) : 1.0F;
        if (bestPickaxeSlot != -1 && bestPickaxeEfficiency > pickaxeEfficiency) priority.add(bestPickaxeSlot);

        int shovelSlot = ItemUtil.findInventorySlot("shovel", 0, true);
        float shovelEfficiency = shovelSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(shovelSlot)) : 1.0F;
        if (bestShovelSlot != -1 && bestShovelEfficiency > shovelEfficiency) priority.add(bestShovelSlot);

        int axeSlot = ItemUtil.findInventorySlot("axe", 0, true);
        float axeEfficiency = axeSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(axeSlot)) : 1.0F;
        if (bestAxeSlot != -1 && bestAxeEfficiency > axeEfficiency) priority.add(bestAxeSlot);

        int bowSlot = ItemUtil.findBowInventorySlot(0, true);
        double bowDamage = bowSlot != -1 ? ItemUtil.getBowAttackBonus(mc.thePlayer.inventory.getStackInSlot(bowSlot)) : 0.0;
        if (bestBow != -1 && bestBowDamage > bowDamage) priority.add(bestBow);

        return priority;
    }

    // ============================================================================
    // 搜索顺序：Distance / Index / Random
    // ============================================================================

    /**
     * 最近邻贪心：从 prioritized 的第一个 slot 开始，每次挑最近的格子（按格子在 9 列网格中的欧氏距离）
     */
    private List<Integer> generateDistanceOrder(IInventory inventory, Container container, List<Integer> prioritySlots) {
        List<Integer> armorSlots = new ArrayList<>();
        List<Integer> swordSlots = new ArrayList<>();
        List<Integer> otherSlots = new ArrayList<>();

        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (prioritySlots.contains(i)) continue; // priority 单独放最前面
            if (container.getSlot(i).getHasStack()) {
                ItemStack stack = container.getSlot(i).getStack();
                if (isValidItem(stack)) {
                    if (stack.getItem() instanceof ItemArmor) {
                        armorSlots.add(i);
                    } else if (stack.getItem() instanceof ItemSword) {
                        swordSlots.add(i);
                    } else {
                        otherSlots.add(i);
                    }
                }
            }
        }

        List<Integer> prioritized = new ArrayList<>(prioritySlots);
        prioritized.addAll(armorSlots);
        prioritized.addAll(swordSlots);
        prioritized.addAll(otherSlots);

        List<Integer> result = new ArrayList<>();
        if (prioritized.isEmpty()) return result;
        boolean[] visited = new boolean[inventory.getSizeInventory()];

        int currentSlot = prioritized.get(0);
        result.add(currentSlot);
        visited[currentSlot] = true;

        while (result.size() < prioritized.size()) {
            int nearestSlot = -1;
            double minDistance = Double.MAX_VALUE;

            for (int slot : prioritized) {
                if (visited[slot]) continue;

                int currentRow = currentSlot / 9;
                int currentCol = currentSlot % 9;
                int slotRow = slot / 9;
                int slotCol = slot % 9;

                double distance = Math.sqrt(Math.pow(currentRow - slotRow, 2) + Math.pow(currentCol - slotCol, 2));

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestSlot = slot;
                }
            }

            if (nearestSlot != -1) {
                result.add(nearestSlot);
                visited[nearestSlot] = true;
                currentSlot = nearestSlot;
            } else {
                break;
            }
        }

        return result;
    }

    /**
     * 按 slot index 升序（自然顺序），但先放 priority / 再放 armor / 再放 sword / 最后其他
     */
    private List<Integer> generateIndexOrder(IInventory inventory, Container container, List<Integer> prioritySlots) {
        List<Integer> armorSlots = new ArrayList<>();
        List<Integer> swordSlots = new ArrayList<>();
        List<Integer> otherSlots = new ArrayList<>();

        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (prioritySlots.contains(i)) continue;
            if (container.getSlot(i).getHasStack()) {
                ItemStack stack = container.getSlot(i).getStack();
                if (isValidItem(stack)) {
                    if (stack.getItem() instanceof ItemArmor) {
                        armorSlots.add(i);
                    } else if (stack.getItem() instanceof ItemSword) {
                        swordSlots.add(i);
                    } else {
                        otherSlots.add(i);
                    }
                }
            }
        }

        List<Integer> prioritized = new ArrayList<>(prioritySlots);
        prioritized.addAll(armorSlots);
        prioritized.addAll(swordSlots);
        prioritized.addAll(otherSlots);
        return prioritized;
    }

    /**
     * Random 模式：armor / sword 保持优先级，其余打乱
     */
    private List<Integer> generateRandomOrder(IInventory inventory, Container container, List<Integer> prioritySlots) {
        List<Integer> armorSlots = new ArrayList<>();
        List<Integer> swordSlots = new ArrayList<>();
        List<Integer> otherSlots = new ArrayList<>();

        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (prioritySlots.contains(i)) continue;
            if (container.getSlot(i).getHasStack()) {
                ItemStack stack = container.getSlot(i).getStack();
                if (isValidItem(stack)) {
                    if (stack.getItem() instanceof ItemArmor) {
                        armorSlots.add(i);
                    } else if (stack.getItem() instanceof ItemSword) {
                        swordSlots.add(i);
                    } else {
                        otherSlots.add(i);
                    }
                }
            }
        }

        List<Integer> prioritized = new ArrayList<>(prioritySlots);
        prioritized.addAll(armorSlots);
        prioritized.addAll(swordSlots);
        Collections.shuffle(otherSlots, random);
        prioritized.addAll(otherSlots);
        return prioritized;
    }

    private void updateStealOrder(IInventory inventory, Container container) {
        if (this.orderGenerated && !stealOrder.isEmpty()) return;

        // 先算最佳装备优先列表（只有 skipTrash=true 时才计算）
        List<Integer> prioritySlots = collectPrioritySlots(container, inventory);

        String mode = this.selectionMode.getModeString();
        switch (mode) {
            case "Distance":
                stealOrder = generateDistanceOrder(inventory, container, prioritySlots);
                break;
            case "Index":
                stealOrder = generateIndexOrder(inventory, container, prioritySlots);
                break;
            case "Random":
                stealOrder = generateRandomOrder(inventory, container, prioritySlots);
                break;
            default:
                stealOrder = generateIndexOrder(inventory, container, prioritySlots);
                break;
        }

        this.currentStealIndex = 0;
        this.orderGenerated = true;
    }

    /**
     * 按当前 stealOrder 顺序尝试偷下一件。如果第一遍扫完没偷到就重新生成一次顺序再扫一遍，
     * 防止用户手动移动了物品后旧 order 失效。都没偷到就返回 false，让上层决定是否 autoClose。
     */
    private boolean trySteal(Container container, IInventory inventory) {
        this.orderGenerated = false;
        updateStealOrder(inventory, container);

        while (this.currentStealIndex < stealOrder.size()) {
            int slotId = stealOrder.get(this.currentStealIndex++);
            if (slotId < 0 || slotId >= container.inventorySlots.size()) continue;
            if (container.getSlot(slotId).getHasStack()) {
                this.shiftClick(container.windowId, slotId);
                return true;
            }
        }

        // 重新生成一次 order，处理玩家手动拖拽 / 部分 slot 变化
        this.orderGenerated = false;
        updateStealOrder(inventory, container);
        while (this.currentStealIndex < stealOrder.size()) {
            int slotId = stealOrder.get(this.currentStealIndex++);
            if (slotId < 0 || slotId >= container.inventorySlots.size()) continue;
            if (container.getSlot(slotId).getHasStack()) {
                this.shiftClick(container.windowId, slotId);
                return true;
            }
        }

        return false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.clickDelay > 0) {
                this.clickDelay--;
            }
            if (this.oDelay > 0) {
                this.oDelay--;
            }
            if (!(mc.currentScreen instanceof GuiChest)) {
                this.inChest = false;
                this.orderGenerated = false;
                this.stealOrder.clear();
                this.currentStealIndex = 0;
            } else {
                Container container = ((GuiChest) mc.currentScreen).inventorySlots;
                if (!(container instanceof ContainerChest)) {
                    this.inChest = false;
                    this.orderGenerated = false;
                    this.stealOrder.clear();
                    this.currentStealIndex = 0;
                } else {
                    if (!this.inChest) {
                        this.inChest = true;
                        this.warnedFull = false;
                        this.oDelay = this.openDelay.getValue() + 1;
                        this.orderGenerated = false;
                        this.stealOrder.clear();
                        this.currentStealIndex = 0;
                    }
                    if (this.oDelay <= 0 && this.clickDelay <= 0) {
                        if (this.isEnabled() && this.isValidGameMode()) {
                            IInventory inventory = ((ContainerChest) container).getLowerChestInventory();
                            if (this.HypixelMode.getValue()) {
                                String inventoryName = inventory.getName();
                                String stripped = inventoryName == null
                                        ? ""
                                        : net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(inventoryName).trim();
                                if (!stripped.isEmpty()) {
                                    return;
                                }
                            } else if (this.nameCheck.getValue()) {
                                String inventoryName = inventory.getName();
                                if (!inventoryName.equals(I18n.format("container.chest")) && !inventoryName.equals(I18n.format("container.chestDouble"))) {
                                    return;
                                }
                            }
                            if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                                if (!this.warnedFull) {
                                    ChatUtil.sendFormatted(String.format("%s%s: &cYour inventory is full!&r", Elara.clientName, this.getName()));
                                    this.warnedFull = true;
                                }
                                if (this.autoClose.getValue()) {
                                    mc.thePlayer.closeScreen();
                                }
                            } else {
                                boolean isZeroDelay = this.minDelay.getValue() == 0 && this.maxDelay.getValue() == 0;
                                int maxIterations = isZeroDelay ? 5 : 1;
                                int stolen = 0;
                                while (stolen < maxIterations) {
                                    if (!trySteal(container, inventory)) {
                                        if (this.autoClose.getValue()) {
                                            mc.thePlayer.closeScreen();
                                        }
                                        break;
                                    }
                                    stolen++;
                                    if (!isZeroDelay) {
                                        this.clickDelay = RandomUtils.nextInt(this.minDelay.getValue() + 1, this.maxDelay.getValue() + 2);
                                    }
                                    if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                                        if (this.autoClose.getValue()) {
                                            mc.thePlayer.closeScreen();
                                        }
                                        break;
                                    }
                                    // 重新拿一次 container/inventory，防止中途状态变化
                                    if (!(mc.currentScreen instanceof GuiChest)) break;
                                    Container c2 = ((GuiChest) mc.currentScreen).inventorySlots;
                                    if (!(c2 instanceof ContainerChest)) break;
                                    container = c2;
                                    inventory = ((ContainerChest) container).getLowerChestInventory();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onWindowClick(WindowClickEvent event) {
        this.orderGenerated = false;
        this.stealOrder.clear();
        this.currentStealIndex = 0;

        if (this.minDelay.getValue() == 0 && this.maxDelay.getValue() == 0) {
            this.clickDelay = 0;
        } else {
            this.clickDelay = RandomUtils.nextInt(this.minDelay.getValue() + 1, this.maxDelay.getValue() + 2);
        }
    }

    @Override
    public void verifyValue(String mode) {
        switch (mode) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
                break;
        }
    }
}
