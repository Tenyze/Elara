package elara.module.misc;

import elara.config.NotificationConfig;
import elara.config.NotificationHelper;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.LoadWorldEvent;
import elara.events.PacketEvent;
import elara.events.TickEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.BooleanProperty;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HackerDetector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // 检测开关属性（flagInterval 和 playSound 已移除，由 NotificationConfig 管理）
    public final BooleanProperty detectAutoBlock = new BooleanProperty("AutoBlock", true);
    public final BooleanProperty detectNoFall = new BooleanProperty("NoFall", true);
    public final BooleanProperty detectNoSlow = new BooleanProperty("NoSlow", true);
    public final BooleanProperty detectScaffold = new BooleanProperty("Scaffold", true);
    public final BooleanProperty detectLegitScaffold = new BooleanProperty("Legit-Scaffold", true);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", false);

    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<UUID, Map<String, Long>> flags = new HashMap<>();
    private long lastClientBoundPacket = 0L;

    public HackerDetector() {
        super("HackerDetector", false, false, "Detects cheaters and alerts with red notification", ModuleCategory.MISC);

        detectAutoBlock.setCategory("Checks");
        detectNoFall.setCategory("Checks");
        detectNoSlow.setCategory("Checks");
        detectScaffold.setCategory("Checks");
        detectLegitScaffold.setCategory("Checks");
        ignoreTeammates.setCategory("Conditions");
    }

    private void alert(EntityPlayer entityPlayer, String mode) {
        if (NotificationConfig.INSTANCE == null) return;
        if (!NotificationConfig.INSTANCE.enabled || !NotificationConfig.INSTANCE.hackerDetectorEnabled) {
            return;
        }
        if (ignoreTeammates.getValue() && isTeammate(entityPlayer)) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        int cooldownSeconds = NotificationConfig.INSTANCE.hackerDetectorCooldown;

        if (cooldownSeconds > 0) {
            Map<String, Long> playerFlags = flags.computeIfAbsent(entityPlayer.getUniqueID(), k -> new HashMap<>());
            Long lastFlag = playerFlags.get(mode);
            if (lastFlag != null && (currentTime - lastFlag) <= (long) cooldownSeconds * 1000L) {
                return;
            }
            playerFlags.put(mode, currentTime);
        }

        String message = entityPlayer.getName() + " - " + mode;
        NotificationHelper.sendError("Hacker Detected!", message);

        if (NotificationConfig.INSTANCE.hackerDetectorSound) {
            try {
                mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
            } catch (Exception ignored) {}
        }
    }

    private boolean isTeammate(EntityPlayer player) {
        try {
            if (mc.thePlayer == null || player == null) return false;
            String myTeam = mc.thePlayer.getDisplayName().getUnformattedText();
            String theirTeam = player.getDisplayName().getUnformattedText();
            if (myTeam.length() > 0 && theirTeam.length() > 0) {
                return myTeam.charAt(0) == theirTeam.charAt(0);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.PRE) return;
        if (mc.isSingleplayer()) return;
        if (mc.theWorld == null) return;

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer entityPlayer = (EntityPlayer) obj;
            if (entityPlayer == mc.thePlayer) continue;
            if (AntiBot.isBot(entityPlayer)) continue;

            PlayerData data = players.get(entityPlayer.getUniqueID());
            if (data == null) {
                data = new PlayerData();
            }
            data.update(entityPlayer);
            this.performCheck(entityPlayer, data);
            data.updateServerPos(entityPlayer);
            data.updateSneak(entityPlayer);
            players.put(entityPlayer.getUniqueID(), data);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() == EventType.RECEIVE) {
            lastClientBoundPacket = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        players.clear();
        flags.clear();
        lastClientBoundPacket = 0L;
    }

    private void performCheck(EntityPlayer entityPlayer, PlayerData playerData) {
        if (detectAutoBlock.getValue() && playerData.autoBlockTicks >= 10) {
            alert(entityPlayer, "AutoBlock");
            return;
        }
        if (detectLegitScaffold.getValue() && playerData.sneakTicks >= 3) {
            alert(entityPlayer, "Legit Scaffold");
            return;
        }
        if (detectNoSlow.getValue() && playerData.noSlowTicks == 11 && playerData.speed >= 0.08) {
            alert(entityPlayer, "NoSlow");
            return;
        }
        if (detectScaffold.getValue() && entityPlayer.isSwingInProgress
                && entityPlayer.rotationPitch >= 70.0F
                && entityPlayer.getHeldItem() != null
                && entityPlayer.getHeldItem().getItem() instanceof ItemBlock
                && playerData.fastTick >= 20
                && entityPlayer.ticksExisted - playerData.lastSneakTick >= 30
                && entityPlayer.ticksExisted - playerData.aboveVoidTicks >= 20) {
            boolean overAir = true;
            BlockPos blockPos = entityPlayer.getPosition().down(2);
            for (int i = 0; i < 4; i++) {
                if (!(mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockAir)) {
                    overAir = false;
                    break;
                }
                blockPos = blockPos.down();
            }
            if (overAir) {
                alert(entityPlayer, "Scaffold");
                return;
            }
        }
        if (detectNoFall.getValue() && !entityPlayer.capabilities.isFlying
                && (System.currentTimeMillis() - lastClientBoundPacket) <= 150) {
            double serverPosX = entityPlayer.serverPosX / 32.0;
            double serverPosY = entityPlayer.serverPosY / 32.0;
            double serverPosZ = entityPlayer.serverPosZ / 32.0;
            double deltaX = Math.abs(playerData.serverPosX - serverPosX);
            double deltaY = playerData.serverPosY - serverPosY;
            double deltaZ = Math.abs(playerData.serverPosZ - serverPosZ);
            if (deltaY >= 5 && deltaX <= 10 && deltaZ <= 10 && deltaY <= 40) {
                if (!isOverVoid(serverPosX, serverPosY, serverPosZ)
                        && distanceToGround(entityPlayer) > 3
                        && !entityPlayer.isOnLadder()
                        && !entityPlayer.isInWater()
                        && !entityPlayer.isInLava()) {
                    alert(entityPlayer, "NoFall");
                }
            }
        }
    }

    private boolean isOverVoid(double x, double y, double z) {
        if (mc.theWorld == null) return false;
        BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
        for (int i = 0; i < 20; i++) {
            if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockAir)) {
                return false;
            }
            pos = pos.down();
            if (pos.getY() < 0) break;
        }
        return true;
    }

    private double distanceToGround(EntityPlayer player) {
        if (mc.theWorld == null) return 0;
        BlockPos pos = player.getPosition();
        for (int i = 0; i < 20; i++) {
            pos = pos.down();
            if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockAir)) {
                return player.posY - pos.getY();
            }
            if (pos.getY() < 0) break;
        }
        return 999;
    }

    @Override
    public void onEnabled() {
        players.clear();
        flags.clear();
        lastClientBoundPacket = 0L;
    }

    @Override
    public void onDisabled() {
        players.clear();
        flags.clear();
        lastClientBoundPacket = 0L;
    }

    // ----- PlayerData 内部类 -----
    private static class PlayerData {
        double speed;
        int aboveVoidTicks;
        int fastTick;
        int autoBlockTicks;
        int ticksExisted;
        int lastSneakTick;
        double posZ;
        int sneakTicks;
        int noSlowTicks;
        double posY;
        boolean sneaking;
        double posX;
        double serverPosX;
        double serverPosY;
        double serverPosZ;

        void update(EntityPlayer entityPlayer) {
            int ticksExisted = entityPlayer.ticksExisted;
            this.posX = entityPlayer.posX - entityPlayer.lastTickPosX;
            this.posY = entityPlayer.posY - entityPlayer.lastTickPosY;
            this.posZ = entityPlayer.posZ - entityPlayer.lastTickPosZ;
            this.speed = Math.max(Math.abs(this.posX), Math.abs(this.posZ));
            if (this.speed >= 0.07) {
                ++this.fastTick;
                this.ticksExisted = ticksExisted;
            } else {
                this.fastTick = 0;
            }
            if (Math.abs(this.posY) >= 0.1) {
                this.aboveVoidTicks = ticksExisted;
            }
            if (entityPlayer.isSneaking()) {
                this.lastSneakTick = ticksExisted;
            }
            if (entityPlayer.isSwingInProgress && entityPlayer.isBlocking()) {
                ++this.autoBlockTicks;
            } else {
                this.autoBlockTicks = 0;
            }
            if (entityPlayer.isSprinting() && entityPlayer.isUsingItem()) {
                ++this.noSlowTicks;
            } else {
                this.noSlowTicks = 0;
            }
            if (entityPlayer.rotationPitch >= 70.0F
                    && entityPlayer.getHeldItem() != null
                    && entityPlayer.getHeldItem().getItem() instanceof ItemBlock) {
                if (entityPlayer.swingProgressInt == 1) {
                    if (!this.sneaking && entityPlayer.isSneaking()) {
                        ++this.sneakTicks;
                    } else {
                        this.sneakTicks = 0;
                    }
                }
            } else {
                this.sneakTicks = 0;
            }
        }

        void updateSneak(EntityPlayer entityPlayer) {
            this.sneaking = entityPlayer.isSneaking();
        }

        void updateServerPos(EntityPlayer entityPlayer) {
            this.serverPosX = entityPlayer.serverPosX / 32.0;
            this.serverPosY = entityPlayer.serverPosY / 32.0;
            this.serverPosZ = entityPlayer.serverPosZ / 32.0;
        }
    }
}