package elara.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;

import java.util.ArrayList;
import java.util.List;

public class PacketUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static List<Packet<INetHandlerPlayClient>> skipReceiveEvent = new ArrayList<>();
    public static List<Packet<?>> skipSendEvent = new ArrayList<>();

    public static void sendPacket(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet);
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        skipSendEvent.add(packet);
        mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
    }
    public static void receivePacketNoEvent(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            skipReceiveEvent.add(casted);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }


    public static void receivePacket(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }
    @SuppressWarnings("unchecked")
    public static <H extends INetHandler> Packet<H> castPacket(Packet<?> packet) throws ClassCastException {
        return (Packet<H>) packet;
    }

    public static boolean isWorldRenderPacket(Packet<?> packet) {
        return packet instanceof S21PacketChunkData
                || packet instanceof S22PacketMultiBlockChange
                || packet instanceof S23PacketBlockChange
                || packet instanceof S24PacketBlockAction
                || packet instanceof S25PacketBlockBreakAnim
                || packet instanceof S26PacketMapChunkBulk
                || packet instanceof S28PacketEffect;
    }
}
