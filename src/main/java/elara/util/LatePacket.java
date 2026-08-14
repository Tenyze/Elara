package elara.util;

import net.minecraft.network.Packet;

public class LatePacket {
    private final Packet<?> packet;
    private final long requiredMs;

    public LatePacket(Packet<?> packet, long requiredMs) {
        this.packet = packet;
        this.requiredMs = requiredMs;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }

    public long getRequiredMs() {
        return this.requiredMs;
    }
}
