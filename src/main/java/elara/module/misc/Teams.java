package elara.module.misc;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.TickEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ModeProperty;
import elara.util.ChatUtil;
import elara.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;

/**
 * Teams - 基于盔甲颜色识别队伍的模块
 *
 * 识别逻辑：
 * 1. 获取玩家穿着的有色皮甲颜色（胸甲优先 > 头盔 > 护腿 > 靴子）
 * 2. 如果对方没有识别到盔甲颜色 → 敌人
 * 3. 如果识别到颜色 → 与自己的盔甲颜色对比，并结合 Tab 列表信息进行验证
 *    - 颜色相同且 Tab 验证通过 → 队友
 *    - 颜色不同 → 敌人
 *    - Tab 明确显示不同队伍 → 敌人（覆盖盔甲判断）
 */
public class Teams extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // 默认未染色皮甲颜色（Minecraft 原生值）
    private static final int DEFAULT_LEATHER_COLOR = 0xA06540;

    // 模式：0=Mixed（组合优先）, 1=Scoreboard（原版队伍系统）, 2=Armor（皮甲颜色）, 3=TagColor（名字颜色：绿色§a直接队友+同色匹配）
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Mixed", "Scoreboard", "Armor", "TagColor"});
    public final BooleanProperty ignoreNoArmor = new BooleanProperty("Ignore-No-Armor", true);
    public final BooleanProperty debug = new BooleanProperty("Debug", false);

    // 缓存自己的盔甲颜色，避免每帧重复计算
    private int selfColor = -1;

    public Teams() {
        super("Teams", false, false, "Detect teammates by scoreboard / armor color / nametag color", ModuleCategory.MISC);
        mode.setCategory("General");
        ignoreNoArmor.setCategory("Armor");
        debug.setCategory("Other");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() != EventType.PRE) return;

        // 更新自己的盔甲颜色
        selfColor = getArmorColor(mc.thePlayer);

        if (debug.getValue() && mc.thePlayer.ticksExisted % 40 == 0) {
            String colorStr = selfColor == -1 ? "None" : String.format("0x%06X", selfColor);
            ChatUtil.sendFormatted(String.format("%sTeams: &fSelf color: &b%s", Elara.clientName, colorStr));
        }
    }

    /**
     * 主入口：按 mode 选项分发判定
     *
     * 0=Mixed     Scoreboard → TagColor → Armor 依次命中即返回
     * 1=Scoreboard  只用原版队伍系统（getTeam() / NetworkPlayerInfo.getPlayerTeam()）
     * 2=Armor      只用皮甲颜色（无盔甲按 ignoreNoArmor 决定是否回退 Scoreboard）
     * 3=TagColor   只用名字颜色前缀：§a绿色→队友，同色（非中性）→队友
     */
    public boolean isSameTeam(EntityPlayer player) {
        if (player == mc.thePlayer) return true;
        if (player == null) return false;

        int m = mode.getValue();
        switch (m) {
            case 1: return isSameTeamByScoreboard(player);
            case 2: return isSameTeamByArmor(player);
            case 3: return isSameTeamByTagColor(player);
            case 0:
            default:
                // Mixed：Scoreboard → TagColor → Armor
                Boolean sb = definiteByScoreboard(player);
                if (sb != null) return sb;
                Boolean tc = definiteByTagColor(player);
                if (tc != null) return tc;
                Boolean ar = definiteByArmor(player);
                if (ar != null) return ar;
                return false;
        }
    }

    // ====================== Scoreboard ======================

    private boolean isSameTeamByScoreboard(EntityPlayer player) {
        // 1. 直接检查 ScorePlayerTeam
        ScorePlayerTeam selfTeam = (ScorePlayerTeam) mc.thePlayer.getTeam();
        ScorePlayerTeam targetTeam = (ScorePlayerTeam) player.getTeam();
        if (selfTeam != null && targetTeam != null) {
            if (selfTeam == targetTeam) return true;
            if (selfTeam.getTeamName().equals(targetTeam.getTeamName())) return true;
            String selfPrefix = selfTeam.getColorPrefix();
            String targetPrefix = targetTeam.getColorPrefix();
            if (selfPrefix != null && !selfPrefix.isEmpty() && selfPrefix.equals(targetPrefix)) {
                return true;
            }
        }
        // 2. 通过 NetworkPlayerInfo 检查
        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (selfInfo != null && targetInfo != null) {
            ScorePlayerTeam selfNpTeam = selfInfo.getPlayerTeam();
            ScorePlayerTeam targetNpTeam = targetInfo.getPlayerTeam();
            if (selfNpTeam != null && targetNpTeam != null) {
                if (selfNpTeam == targetNpTeam) return true;
                if (selfNpTeam.getTeamName().equals(targetNpTeam.getTeamName())) return true;
                String selfPrefix = selfNpTeam.getColorPrefix();
                String targetPrefix = targetNpTeam.getColorPrefix();
                if (selfPrefix != null && !selfPrefix.isEmpty() && selfPrefix.equals(targetPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Scoreboard 三态判断：true=明确队友 / false=明确敌人 / null=无队伍信息
     */
    private Boolean definiteByScoreboard(EntityPlayer player) {
        ScorePlayerTeam selfTeam = (ScorePlayerTeam) mc.thePlayer.getTeam();
        ScorePlayerTeam targetTeam = (ScorePlayerTeam) player.getTeam();
        if (selfTeam != null && targetTeam != null) {
            if (selfTeam == targetTeam) return Boolean.TRUE;
            if (selfTeam.getTeamName().equals(targetTeam.getTeamName())) return Boolean.TRUE;
            String selfPrefix = selfTeam.getColorPrefix();
            String targetPrefix = targetTeam.getColorPrefix();
            if (selfPrefix != null && !selfPrefix.isEmpty() && selfPrefix.equals(targetPrefix)) {
                return Boolean.TRUE;
            }
            // 双方都有队伍对象但不同：视为明确敌人
            return Boolean.FALSE;
        }
        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (selfInfo != null && targetInfo != null) {
            ScorePlayerTeam selfNpTeam = selfInfo.getPlayerTeam();
            ScorePlayerTeam targetNpTeam = targetInfo.getPlayerTeam();
            if (selfNpTeam != null && targetNpTeam != null) {
                if (selfNpTeam == targetNpTeam) return Boolean.TRUE;
                if (selfNpTeam.getTeamName().equals(targetNpTeam.getTeamName())) return Boolean.TRUE;
                String selfPrefix = selfNpTeam.getColorPrefix();
                String targetPrefix = targetNpTeam.getColorPrefix();
                if (selfPrefix != null && !selfPrefix.isEmpty() && selfPrefix.equals(targetPrefix)) {
                    return Boolean.TRUE;
                }
                return Boolean.FALSE;
            }
        }
        return null;
    }

    // ====================== Armor ======================

    private boolean isSameTeamByArmor(EntityPlayer player) {
        int myColor = selfColor != -1 ? selfColor : getArmorColor(mc.thePlayer);
        int theirColor = getArmorColor(player);

        if (theirColor == -1) {
            // 对方没盔甲颜色
            if (!ignoreNoArmor.getValue()) {
                // 不忽略无盔甲玩家：回退 Scoreboard
                return isSameTeamByScoreboard(player);
            }
            return false;
        }
        if (myColor == -1) {
            // 自己没盔甲颜色：回退 Scoreboard
            return isSameTeamByScoreboard(player);
        }
        return myColor == theirColor;
    }

    private Boolean definiteByArmor(EntityPlayer player) {
        int myColor = selfColor != -1 ? selfColor : getArmorColor(mc.thePlayer);
        int theirColor = getArmorColor(player);
        if (theirColor == -1 && ignoreNoArmor.getValue()) return null;
        if (myColor == -1 || theirColor == -1) return null; // 无法比较
        return myColor == theirColor;
    }

    // ====================== TagColor (名字颜色前缀) ======================

    /**
     * 提取玩家 displayName 的首个颜色前缀。
     * §a=亮绿  §2=深绿  §1-§6 §9-§e 这些是队伍常见颜色。
     * 中性色 §0(黑) §7(灰) §8(深灰) §f(白) 视为"无有效队伍颜色"。
     */
    private static String extractColorPrefix(EntityPlayer player) {
        if (player == null) return null;
        String name;
        try {
            name = player.getDisplayName().getFormattedText();
        } catch (Throwable t) {
            return null;
        }
        if (name == null || name.isEmpty()) return null;
        // 查找第一个 § 颜色代码
        for (int i = 0; i < name.length() - 1; i++) {
            if (name.charAt(i) != '§') continue;
            char c = Character.toLowerCase(name.charAt(i + 1));
            if ("0123456789abcdef".indexOf(c) != -1) {
                if (c == '0' || c == '7' || c == '8' || c == 'f') {
                    // 中性色：跳过，继续找后面可能的非中性色
                    continue;
                }
                return "§" + c;
            }
        }
        return null;
    }

    /**
     * 判断一个颜色前缀是否是"绿色系"（§a 亮绿、§2 深绿）
     * 用户需求："绿色的识别为队友"
     */
    private static boolean isGreenColor(String prefix) {
        return "§a".equals(prefix) || "§2".equals(prefix);
    }

    /**
     * TagColor 模式：绿色→队友，同色（非中性）→队友，否则敌人
     */
    private boolean isSameTeamByTagColor(EntityPlayer player) {
        String selfPrefix = extractColorPrefix(mc.thePlayer);
        String targetPrefix = extractColorPrefix(player);

        // 绿色系名字：直接判定为队友（有些服务器队友就是标绿不管自己色）
        if (isGreenColor(targetPrefix)) {
            return true;
        }

        // 同色（非中性）前缀匹配 → 队友
        if (selfPrefix != null && targetPrefix != null && selfPrefix.equals(targetPrefix)) {
            return true;
        }

        return false;
    }

    private Boolean definiteByTagColor(EntityPlayer player) {
        String selfPrefix = extractColorPrefix(mc.thePlayer);
        String targetPrefix = extractColorPrefix(player);

        // 对方明确是绿色 → 队友（命中即返回）
        if (isGreenColor(targetPrefix)) return Boolean.TRUE;
        // 自己和对方都有有效前缀且相同 → 队友；不同 → 敌人
        if (selfPrefix != null && targetPrefix != null) {
            return selfPrefix.equals(targetPrefix);
        }
        // 无法判定（其中一方没有有效颜色）
        return null;
    }

    /**
     * 获取玩家穿着的有色皮甲颜色
     * 优先级：胸甲 > 头盔 > 护腿 > 靴子
     *
     * @param player 目标玩家
     * @return RGB 颜色值，-1 表示没有有色皮甲
     */
    public int getArmorColor(EntityPlayer player) {
        if (player == null) return -1;

        // 按优先级检查（Bedwars 中胸甲颜色通常代表队伍）
        int[] slots = {2, 3, 1, 0}; // 胸甲、头盔、护腿、靴子

        for (int slot : slots) {
            ItemStack stack = player.inventory.armorInventory[slot];
            if (stack == null) continue;
            if (!(stack.getItem() instanceof ItemArmor)) continue;

            ItemArmor armor = (ItemArmor) stack.getItem();
            // 只识别皮甲（非皮甲 getColor 返回 -1）
            int color = armor.getColor(stack);

            // 排除无效颜色和默认未染色皮甲颜色
            if (color < 0) continue;
            if (color == DEFAULT_LEATHER_COLOR) continue;

            return color;
        }

        return -1;
    }

    /**
     * 判断玩家是否穿着有色皮甲
     */
    public boolean hasColoredArmor(EntityPlayer player) {
        return getArmorColor(player) != -1;
    }

    @Override
    public void onDisabled() {
        selfColor = -1;
    }

    /**
     * 静态方法，供其他模块调用（如 KillAura、AntiBot 等）
     * 判断玩家是否是队友
     */
    public static boolean isTeammate(EntityPlayer player) {
        Teams armorTeam = (Teams) Elara.moduleManager.getModule(Teams.class);
        if (armorTeam == null || !armorTeam.isEnabled()) {
            return false;
        }
        return armorTeam.isSameTeam(player);
    }

    /**
     * 静态方法，判断玩家是否穿着有色皮甲
     */
    public static boolean hasColor(EntityPlayer player) {
        Teams armorTeam = (Teams) Elara.moduleManager.getModule(Teams.class);
        if (armorTeam == null || !armorTeam.isEnabled()) {
            return false;
        }
        return armorTeam.hasColoredArmor(player);
    }

    // ====================== 颜色匹配（供 ESP / Tracers 等渲染使用） ======================

    private static final int COLOR_ENEMY_DEFAULT = 0xFFFF3030; // 亮红
    private static final int COLOR_MATE_DEFAULT = 0xFF30B0FF;  // 亮蓝

    /**
     * 将 Minecraft § 颜色代码（如 "§a"）转换为 RGB int。
     * 未识别/中性色返回 -1。
     */
    private static int codeToRgb(char c) {
        switch (Character.toLowerCase(c)) {
            case '0': return 0x000000;
            case '1': return 0x0000AA;
            case '2': return 0x00AA00;
            case '3': return 0x00AAAA;
            case '4': return 0xAA0000;
            case '5': return 0xAA00AA;
            case '6': return 0xFFAA00;
            case '7': return 0xAAAAAA;
            case '8': return 0x555555;
            case '9': return 0x5555FF;
            case 'a': return 0x55FF55;
            case 'b': return 0x55FFFF;
            case 'c': return 0xFF5555;
            case 'd': return 0xFF55FF;
            case 'e': return 0xFFFF55;
            case 'f': return 0xFFFFFF;
            default:  return -1;
        }
    }

    private static int prefixToRgb(String prefix) {
        if (prefix == null || prefix.length() < 2 || prefix.charAt(0) != '§') return -1;
        return codeToRgb(prefix.charAt(1));
    }

    /**
     * 按当前 Teams.mode 为玩家取 ESP/Tracers 颜色。
     * 队友 → 对应 mode 下的识别颜色（盔甲色/Tag 色/Scoreboard 色）
     * 敌人 → 红色系
     * 无法判定 → 默认蓝(队友)/红(敌人)
     */
    public int getTeamColorOf(EntityPlayer player) {
        if (player == mc.thePlayer) return 0xFFFFFFFF;
        int m = mode.getValue();

        boolean sameTeam = isSameTeam(player);
        int fallback = sameTeam ? COLOR_MATE_DEFAULT : COLOR_ENEMY_DEFAULT;

        switch (m) {
            case 2: { // Armor 模式：队友→盔甲色；敌人→敌人色
                if (sameTeam) {
                    int c = getArmorColor(player);
                    return c != -1 ? withAlpha(c, 0xFF) : fallback;
                }
                return fallback;
            }
            case 3: { // TagColor 模式：队友→Tag 颜色；敌人→敌人色
                if (sameTeam) {
                    String p = extractColorPrefix(player);
                    int rgb = prefixToRgb(p);
                    return rgb != -1 ? withAlpha(rgb, 0xFF) : fallback;
                }
                return fallback;
            }
            case 1: { // Scoreboard 模式：取 player team 的 colorPrefix（无论敌我都按队伍颜色显示更直观）
                ScorePlayerTeam t = (ScorePlayerTeam) player.getTeam();
                if (t != null) {
                    int rgb = prefixToRgb(t.getColorPrefix());
                    if (rgb != -1) return withAlpha(rgb, 0xFF);
                }
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
                if (info != null && info.getPlayerTeam() != null) {
                    int rgb = prefixToRgb(info.getPlayerTeam().getColorPrefix());
                    if (rgb != -1) return withAlpha(rgb, 0xFF);
                }
                return fallback;
            }
            case 0:
            default: { // Mixed：Scoreboard → TagColor → Armor
                ScorePlayerTeam t = (ScorePlayerTeam) player.getTeam();
                if (t != null) {
                    int rgb = prefixToRgb(t.getColorPrefix());
                    if (rgb != -1) return withAlpha(rgb, 0xFF);
                }
                if (sameTeam) {
                    String p = extractColorPrefix(player);
                    int rgbTag = prefixToRgb(p);
                    if (rgbTag != -1) return withAlpha(rgbTag, 0xFF);
                    int cArmor = getArmorColor(player);
                    if (cArmor != -1) return withAlpha(cArmor, 0xFF);
                }
                return fallback;
            }
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * 静态入口：ESP teams 模式用。
     * Teams 模块未启用时 fallback：队友蓝色 / 敌人红色。
     */
    public static int getColorForESP(EntityPlayer player) {
        Teams teams = (Teams) Elara.moduleManager.getModule(Teams.class);
        if (teams != null && teams.isEnabled()) {
            return teams.getTeamColorOf(player);
        }
        boolean sameTeam = TeamUtil.isSameTeamByScoreboard(player);
        return sameTeam ? COLOR_MATE_DEFAULT : COLOR_ENEMY_DEFAULT;
    }

    /**
     * 获取玩家的原始检测颜色（不分敌友）。
     * 队友和敌人都返回其队伍识别到的颜色，用于 ESP Color 模式。
     */
    public int getDetectedColor(EntityPlayer player) {
        if (player == mc.thePlayer) return 0xFFFFFFFF;
        int m = mode.getValue();

        switch (m) {
            case 2: { // Armor
                int c = getArmorColor(player);
                return c != -1 ? withAlpha(c, 0xFF) : COLOR_ENEMY_DEFAULT;
            }
            case 3: { // TagColor
                String p = extractColorPrefix(player);
                int rgb = prefixToRgb(p);
                return rgb != -1 ? withAlpha(rgb, 0xFF) : COLOR_ENEMY_DEFAULT;
            }
            case 1: { // Scoreboard
                ScorePlayerTeam t = (ScorePlayerTeam) player.getTeam();
                if (t != null) {
                    int rgb = prefixToRgb(t.getColorPrefix());
                    if (rgb != -1) return withAlpha(rgb, 0xFF);
                }
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
                if (info != null && info.getPlayerTeam() != null) {
                    int rgb = prefixToRgb(info.getPlayerTeam().getColorPrefix());
                    if (rgb != -1) return withAlpha(rgb, 0xFF);
                }
                return COLOR_ENEMY_DEFAULT;
            }
            case 0:
            default: { // Mixed: Scoreboard → TagColor → Armor
                ScorePlayerTeam t = (ScorePlayerTeam) player.getTeam();
                if (t != null) {
                    int rgb = prefixToRgb(t.getColorPrefix());
                    if (rgb != -1) return withAlpha(rgb, 0xFF);
                }
                String p = extractColorPrefix(player);
                int rgbTag = prefixToRgb(p);
                if (rgbTag != -1) return withAlpha(rgbTag, 0xFF);
                int cArmor = getArmorColor(player);
                if (cArmor != -1) return withAlpha(cArmor, 0xFF);
                return COLOR_ENEMY_DEFAULT;
            }
        }
    }

    /**
     * 静态入口：ESP color 模式用，返回玩家的原始检测颜色。
     */
    public static int getDetectedColorForESP(EntityPlayer player) {
        Teams teams = (Teams) Elara.moduleManager.getModule(Teams.class);
        if (teams != null && teams.isEnabled()) {
            return teams.getDetectedColor(player);
        }
        return COLOR_ENEMY_DEFAULT;
    }
}
