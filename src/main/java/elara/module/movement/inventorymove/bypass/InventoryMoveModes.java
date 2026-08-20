package elara.module.movement.inventorymove.bypass;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.module.movement.InventoryMove;

public class InventoryMoveModes {
    private final InventoryMove parent;

    public final NormalBypass normal;
    public final BufferAbuseBypass bufferAbuse;
    public final CancelBypass cancel;
    public final Grim2Bypass grim2;
    public final WatchdogBypass watchdog;

    public InventoryMoveModes(InventoryMove parent) {
        this.parent = parent;
        this.normal = new NormalBypass(parent);
        this.bufferAbuse = new BufferAbuseBypass(parent);
        this.cancel = new CancelBypass(parent);
        this.grim2 = new Grim2Bypass(parent);
        this.watchdog = new WatchdogBypass(parent);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        switch (parent.mode.getValue()) {
            case 0: normal.onUpdate(event); break;
            case 1: bufferAbuse.onUpdate(event); break;
            case 2: cancel.onUpdate(event); break;
            case 3: grim2.onUpdate(event); break;
            case 4: watchdog.onUpdate(event); break;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND) return;
        switch (parent.mode.getValue()) {
            case 1: bufferAbuse.onPacket(event); break;
            case 2: cancel.onPacket(event); break;
            case 3: grim2.onPacket(event); break;
            case 4: watchdog.onPacket(event); break;
        }
    }

    public void onDisable() {
        bufferAbuse.onDisable();
        cancel.onDisable();
        watchdog.onDisable();
    }
}
