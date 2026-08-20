package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.TickEvent;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class DelayVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final IntProperty delayTicks = new IntProperty("Delay Ticks", 5, 0, 40);
    public final BooleanProperty pingSpoof = new BooleanProperty("Delay Explosions", true);

    private static class QueuedPacket {
        final Packet<?> packet;
        final int scheduledTick;

        QueuedPacket(Packet<?> packet, int scheduledTick) {
            this.packet = packet;
            this.scheduledTick = scheduledTick;
        }
    }

    private final Deque<QueuedPacket> velocityPackets = new ArrayDeque<>();
    private boolean applying = false;

    public DelayVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || applying) return;
        if (mc.thePlayer == null) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
            if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
                event.setCancelled(true);
                int i = delayTicks.getValue();
                int j = mc.thePlayer.ticksExisted;
                velocityPackets.addLast(new QueuedPacket(s12, j + i));
            }
        } else if (packet instanceof S32PacketConfirmTransaction) {
            event.setCancelled(true);
            int k = delayTicks.getValue();
            int l = mc.thePlayer.ticksExisted;
            velocityPackets.addLast(new QueuedPacket(packet, l + k));
        } else if (pingSpoof.getValue() && packet instanceof S27PacketExplosion) {
            event.setCancelled(true);
            int i1 = delayTicks.getValue();
            int j1 = mc.thePlayer.ticksExisted;
            velocityPackets.addLast(new QueuedPacket(packet, j1 + i1));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || velocityPackets.isEmpty()) return;

        int currentTick = mc.thePlayer.ticksExisted;
        while (!velocityPackets.isEmpty() && velocityPackets.peekFirst().scheduledTick <= currentTick) {
            Packet<?> packet = velocityPackets.removeFirst().packet;
            applying = true;
            try {
                PacketUtil.receivePacketNoEvent(packet);
            } finally {
                applying = false;
            }
        }
    }

    public void onDisable() {
        if (!velocityPackets.isEmpty()) {
            applying = true;
            try {
                Iterator<QueuedPacket> iterator = velocityPackets.iterator();
                while (iterator.hasNext()) {
                    PacketUtil.receivePacketNoEvent(iterator.next().packet);
                }
            } finally {
                applying = false;
                velocityPackets.clear();
            }
        }
    }
}
