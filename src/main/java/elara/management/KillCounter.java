package elara.management;

import elara.config.NotificationHelper;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.AttackEvent;
import elara.events.LoadWorldEvent;
import elara.events.PacketEvent;
import elara.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S13PacketDestroyEntities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillCounter {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long ATTACK_TIMEOUT_MS = 10000L;

    private int killCount = 0;
    private final Map<UUID, Long> recentAttacks = new HashMap<>();
    private final Map<Integer, UUID> entityIdToUuid = new HashMap<>();

    public KillCounter() {
    }

    public int getKillCount() {
        return killCount;
    }

    public void reset() {
        killCount = 0;
        recentAttacks.clear();
        entityIdToUuid.clear();
    }

    public void recordAttack(EntityLivingBase target) {
        if (target == null) return;
        if (target == mc.thePlayer) return;
        UUID uuid = target.getUniqueID();
        recentAttacks.put(uuid, System.currentTimeMillis());
        entityIdToUuid.put(target.getEntityId(), uuid);
    }

    private void checkEntityDestroy(int entityId) {
        UUID uuid = entityIdToUuid.remove(entityId);
        if (uuid == null) return;

        Long attackTime = recentAttacks.get(uuid);
        long now = System.currentTimeMillis();
        if (attackTime != null && (now - attackTime) <= ATTACK_TIMEOUT_MS) {
            killCount++;
            recentAttacks.remove(uuid);

            EntityPlayer player = null;
            if (mc.theWorld != null) {
                for (Object obj : mc.theWorld.playerEntities) {
                    if (obj instanceof EntityPlayer) {
                        EntityPlayer p = (EntityPlayer) obj;
                        if (p.getUniqueID().equals(uuid)) {
                            player = p;
                            break;
                        }
                    }
                }
            }
            String playerName = player != null ? player.getName() : "Unknown";
            NotificationHelper.send("Kill +1", playerName);
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        recentAttacks.entrySet().removeIf(e -> now - e.getValue() > ATTACK_TIMEOUT_MS);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof EntityLivingBase) {
            recordAttack((EntityLivingBase) event.getTarget());
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.getPacket() instanceof S13PacketDestroyEntities) {
            S13PacketDestroyEntities destroy = (S13PacketDestroyEntities) event.getPacket();
            for (int id : destroy.getEntityIDs()) {
                checkEntityDestroy(id);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        cleanupExpired();
        if (mc.theWorld != null) {
            for (Object obj : mc.theWorld.playerEntities) {
                if (obj instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) obj;
                    if (player != mc.thePlayer) {
                        entityIdToUuid.put(player.getEntityId(), player.getUniqueID());
                    }
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        reset();
    }
}
