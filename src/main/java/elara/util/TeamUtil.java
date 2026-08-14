package elara.util;

import elara.Elara;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class TeamUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isEntityLoaded(Entity entity) {
        if (entity == null) return false;
        return TeamUtil.mc.theWorld.loadedEntityList.contains(entity);
    }

    public static List<Entity> getLoadedEntitiesSorted() {
        return TeamUtil.mc.theWorld.loadedEntityList.stream().sorted((entity1, entity2) -> {
            double dist1 = mc.getRenderManager().getDistanceToCamera(entity1.posX, entity1.posY, entity1.posZ);
            double dist2 = mc.getRenderManager().getDistanceToCamera(entity2.posX, entity2.posY, entity2.posZ);
            if (dist1 < dist2) {
                return 1;
            }
            if (dist1 > dist2) {
                return -1;
            }
            return entity1.getUniqueID().toString().compareTo(entity2.getUniqueID().toString());
        }).collect(Collectors.toList());
    }

    public static float getHealthScore(EntityLivingBase entityLivingBase) {
        return entityLivingBase.getHealth() * (20.0f / (float) entityLivingBase.getTotalArmorValue());
    }

    public static String stripName(Entity entity) {
        return entity.getDisplayName().getFormattedText().replaceAll("§\\S$", "").replaceAll("(?i)§r", "§f").trim();
    }

    public static Color getTeamColor(EntityPlayer player, float alpha) {
        int colorCode = 0xFFFFFF;
        ScorePlayerTeam playerTeam = (ScorePlayerTeam) player.getTeam();
        if (playerTeam != null) {
            String colorPrefix = FontRenderer.getFormatFromString(playerTeam.getColorPrefix());
            if (colorPrefix.length() >= 2) {
                colorCode = TeamUtil.mc.fontRendererObj.getColorCode(colorPrefix.charAt(1));
            }
        }
        return new Color(colorCode & 0xFFFFFF | (int)(alpha * 255) << 24, true);
    }

    public static boolean isBot(EntityPlayer player) {
        if (player == TeamUtil.mc.thePlayer) {
            return false;
        }
        NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(player.getName());
        if (playerInfo == null) {
            return true;
        }
        if (!ServerUtil.isHypixel()) return false;
        if (player.getName().startsWith("§k")) {
            return player.isInvisible();
        }
        if (playerInfo.getResponseTime() < 1) {
            return true;
        }
        ScorePlayerTeam playerTeam = playerInfo.getPlayerTeam();
        if (playerTeam == null) return false;
        if (!playerTeam.getTeamName().isEmpty()) return false;
        return playerTeam.getColorPrefix().equals("§c");
    }

    public static boolean isSameTeam(EntityPlayer player) {
        if (player == TeamUtil.mc.thePlayer) {
            return true;
        }

        // 优先走 Teams 模块的模式判定（如果启用）
        // 这样 KillAura / AimAssist / ESP / Tracers / HitBox / BackTrack 等
        // 所有调用 TeamUtil.isSameTeam 的模块，都会统一应用 Teams 的 Mode 选项
        elara.module.misc.Teams armorTeam = (elara.module.misc.Teams) Elara.moduleManager.getModule(elara.module.misc.Teams.class);
        if (armorTeam != null && armorTeam.isEnabled()) {
            return armorTeam.isSameTeam(player);
        }

        // ========== 未启用 Teams 时的兜底：原版 Scoreboard + 颜色前缀 ==========

        return isSameTeamByScoreboard(player);
    }

    /**
     * 按指定 mode 独立判断队伍，不依赖 Teams 模块
     * mode: 0=Mixed, 1=Scoreboard, 2=Armor, 3=TagColor
     */
    public static boolean isSameTeam(EntityPlayer player, int mode, boolean ignoreNoArmor) {
        if (player == mc.thePlayer) return true;
        if (player == null) return false;

        switch (mode) {
            case 1: return isSameTeamByScoreboard(player);
            case 2: return isSameTeamByArmor(player, ignoreNoArmor);
            case 3: return isSameTeamByTagColor(player);
            case 0:
            default: {
                // Mixed：Scoreboard → TagColor → Armor
                Boolean sb = definiteByScoreboard(player);
                if (sb != null) return sb;
                Boolean tc = definiteByTagColor(player);
                if (tc != null) return tc;
                Boolean ar = definiteByArmor(player, ignoreNoArmor);
                if (ar != null) return ar;
                return false;
            }
        }
    }

    public static boolean hasTeamColor(EntityLivingBase entity) {
        if (entity == TeamUtil.mc.thePlayer) {
            return true;
        }
        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(TeamUtil.mc.thePlayer.getUniqueID());
        if (selfInfo == null) {
            return false;
        }
        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        if (selfTeam == null) {
            return false;
        }
        if (selfTeam.getColorPrefix().length() < 2) {
            return false;
        }
        EntityLivingBase nearestArmorStand = TeamUtil.mc.theWorld.findNearestEntityWithinAABB(EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        if (nearestArmorStand != null) {
            return nearestArmorStand.getName().contains(selfTeam.getColorPrefix().substring(0, 2));
        }
        return false;
    }

    public static boolean isShop(EntityLivingBase entity) {
        if (entity == TeamUtil.mc.thePlayer) {
            return false;
        }
        EntityLivingBase armorStand = TeamUtil.mc.theWorld.findNearestEntityWithinAABB(EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        if (armorStand == null) return false;
        String displayName = armorStand.getName();
        if (displayName.contains("RIGHT CLICK")) return true;
        if (displayName.contains("ITEM SHOP")) return true;
        if (displayName.contains("UPGRADES")) return true;
        if (displayName.contains("BANKER")) return true;
        return displayName.contains("STREAK POWERS");
    }

    public static boolean isFriend(EntityPlayer player) {
        return Elara.friendManager.isFriend(player.getName());
    }

    public static boolean isTarget(EntityPlayer player) {
        return Elara.targetManager.isFriend(player.getName());
    }

    public static boolean isSameTeamByNamePrefix(EntityPlayer player) {
        if (player == TeamUtil.mc.thePlayer) {
            return true;
        }
        
        String selfName = mc.thePlayer.getDisplayName().getFormattedText();
        String targetName = player.getDisplayName().getFormattedText();
        
        String selfColor = extractColorPrefix(selfName);
        String targetColor = extractColorPrefix(targetName);
        
        if (selfColor != null && targetColor != null && selfColor.equals(targetColor)) {
            return true;
        }
        
        return false;
    }

    private static String extractColorPrefix(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        if (name.length() >= 2 && name.charAt(0) == '§') {
            char colorCode = name.charAt(1);
            if ("0123456789abcdef".indexOf(colorCode) != -1) {
                // 排除中性/默认颜色：§0(黑) §7(灰) §8(深灰) §f(白)
                // 这些不是队伍颜色。在 FFA/空岛战争等无队伍模式中，所有玩家
                // 共享这些默认名字颜色，若不排除会把所有人误判成队友导致 KillAura 不打人。
                // 真正的队伍颜色（起床战争等）使用 §1-§6 §9 §a-§e 等亮色，仍会正常匹配。
                if (colorCode == '0' || colorCode == '7' || colorCode == '8' || colorCode == 'f') {
                    return null;
                }
                return "§" + colorCode;
            }
        }

        return null;
    }

    public static boolean isSameTeamAdvanced(EntityPlayer player) {
        // Teams 启用时直接走它的 Mode 判定（本身已包含多种策略，不需要再叠加）
        elara.module.misc.Teams armorTeam = (elara.module.misc.Teams) Elara.moduleManager.getModule(elara.module.misc.Teams.class);
        if (armorTeam != null && armorTeam.isEnabled()) {
            return armorTeam.isSameTeam(player);
        }

        if (isSameTeam(player)) {
            return true;
        }

        if (isSameTeamByNamePrefix(player)) {
            return true;
        }

        return false;
    }

    // ====================== 按 mode 独立判断的静态方法 ======================

    private static final int DEFAULT_LEATHER_COLOR = 0xA06540;

    public static boolean isSameTeamByScoreboard(EntityPlayer player) {
        if (player == mc.thePlayer) return true;

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

    private static Boolean definiteByScoreboard(EntityPlayer player) {
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

    public static boolean isSameTeamByArmor(EntityPlayer player, boolean ignoreNoArmor) {
        int myColor = getArmorColorStatic(mc.thePlayer);
        int theirColor = getArmorColorStatic(player);

        if (theirColor == -1) {
            if (!ignoreNoArmor) {
                return isSameTeamByScoreboard(player);
            }
            return false;
        }
        if (myColor == -1) {
            return isSameTeamByScoreboard(player);
        }
        return myColor == theirColor;
    }

    private static Boolean definiteByArmor(EntityPlayer player, boolean ignoreNoArmor) {
        int myColor = getArmorColorStatic(mc.thePlayer);
        int theirColor = getArmorColorStatic(player);
        if (theirColor == -1 && ignoreNoArmor) return null;
        if (myColor == -1 || theirColor == -1) return null;
        return myColor == theirColor;
    }

    public static boolean isSameTeamByTagColor(EntityPlayer player) {
        String selfPrefix = extractColorPrefixStatic(mc.thePlayer);
        String targetPrefix = extractColorPrefixStatic(player);

        if (isGreenColor(targetPrefix)) {
            return true;
        }
        if (selfPrefix != null && targetPrefix != null && selfPrefix.equals(targetPrefix)) {
            return true;
        }
        return false;
    }

    private static Boolean definiteByTagColor(EntityPlayer player) {
        String selfPrefix = extractColorPrefixStatic(mc.thePlayer);
        String targetPrefix = extractColorPrefixStatic(player);

        if (isGreenColor(targetPrefix)) return Boolean.TRUE;
        if (selfPrefix != null && targetPrefix != null) {
            return selfPrefix.equals(targetPrefix);
        }
        return null;
    }

    public static int getArmorColorStatic(EntityPlayer player) {
        if (player == null) return -1;
        int[] slots = {2, 3, 1, 0};
        for (int slot : slots) {
            ItemStack stack = player.inventory.armorInventory[slot];
            if (stack == null) continue;
            if (!(stack.getItem() instanceof ItemArmor)) continue;
            ItemArmor armor = (ItemArmor) stack.getItem();
            int color = armor.getColor(stack);
            if (color < 0) continue;
            if (color == DEFAULT_LEATHER_COLOR) continue;
            return color;
        }
        return -1;
    }

    private static String extractColorPrefixStatic(EntityPlayer player) {
        if (player == null) return null;
        String name;
        try {
            name = player.getDisplayName().getFormattedText();
        } catch (Throwable t) {
            return null;
        }
        if (name == null || name.isEmpty()) return null;
        for (int i = 0; i < name.length() - 1; i++) {
            if (name.charAt(i) != '§') continue;
            char c = Character.toLowerCase(name.charAt(i + 1));
            if ("0123456789abcdef".indexOf(c) != -1) {
                if (c == '0' || c == '7' || c == '8' || c == 'f') continue;
                return "§" + c;
            }
        }
        return null;
    }

    private static boolean isGreenColor(String prefix) {
        return "§a".equals(prefix) || "§2".equals(prefix);
    }
}
