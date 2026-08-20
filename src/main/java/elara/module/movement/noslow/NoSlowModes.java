package elara.module.movement.noslow;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.MotionEvent;
import elara.events.PacketEvent;
import elara.events.PlayerUpdateEvent;
import elara.events.RightClickMouseEvent;
import elara.events.SlowDownEvent;
import elara.events.StrafeEvent;
import elara.module.movement.NoSlow;

public class NoSlowModes {
    private final NoSlow parent;

    public final VanillaNoSlow vanilla;
    public final NCPNoSlow ncp;
    public final NewNCPNoSlow newNcp;
    public final IntaveNoSlow intave;
    public final LegitNoSlow legit;
    public final WatchdogPredictionNoSlow watchdogPrediction;
    public final VariableNoSlow variable;
    public final PredictionNoSlow prediction;
    public final WatchdogNoSlow watchdog;
    public final Grim19NoSlow grim19;
    public final GrimNoSlow grim;
    public final Grim30NoSlow grim30;
    public final MatrixNoSlow matrix;

    public NoSlowModes(NoSlow parent) {
        this.parent = parent;
        this.vanilla = new VanillaNoSlow(parent);
        this.ncp = new NCPNoSlow(parent);
        this.newNcp = new NewNCPNoSlow(parent);
        this.intave = new IntaveNoSlow(parent);
        this.legit = new LegitNoSlow(parent);
        this.watchdogPrediction = new WatchdogPredictionNoSlow(parent);
        this.variable = new VariableNoSlow(parent);
        this.prediction = new PredictionNoSlow(parent);
        this.watchdog = new WatchdogNoSlow(parent);
        this.grim19 = new Grim19NoSlow(parent);
        this.grim = new GrimNoSlow(parent);
        this.grim30 = new Grim30NoSlow(parent);
        this.matrix = new MatrixNoSlow(parent);
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        switch (parent.mode.getValue()) {
            case 0: vanilla.onSlowDown(event); break;
            case 1: ncp.onSlowDown(event); break;
            case 2: newNcp.onSlowDown(event); break;
            case 3: intave.onSlowDown(event); break;
            case 4: legit.onSlowDown(event); break;
            case 5: watchdogPrediction.onSlowDown(event); break;
            case 6: variable.onSlowDown(event); break;
            case 7: prediction.onSlowDown(event); break;
            case 8: watchdog.onSlowDown(event); break;
            case 9: grim19.onSlowDown(event); break;
            case 10: grim.onSlowDown(event); break;
            case 11: grim30.onSlowDown(event); break;
            case 12: matrix.onSlowDown(event); break;
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getEventState() == MotionEvent.EventState.PRE) {
            switch (parent.mode.getValue()) {
                case 1: ncp.onPreMotion(event); break;
                case 2: newNcp.onPreMotion(event); break;
                case 3: intave.onPreMotion(event); break;
                case 4: legit.onPreMotion(event); break;
                case 5: watchdogPrediction.onPreMotion(event); break;
                case 8: watchdog.onPreMotion(event); break;
                case 10: grim.onPreMotion(event); break;
            }
        } else {
            switch (parent.mode.getValue()) {
                case 1: ncp.onPostMotion(event); break;
            }
        }
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        switch (parent.mode.getValue()) {
            case 5: watchdogPrediction.onPlayerUpdate(event); break;
            case 11: grim30.onPlayerUpdate(event); break;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        switch (parent.mode.getValue()) {
            case 4:
                if (event.getType() == EventType.RECEIVE) legit.onPacket(event);
                break;
            case 11:
                if (event.getType() == EventType.SEND) grim30.onPacket(event);
                break;
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        switch (parent.mode.getValue()) {
            case 8: watchdog.onRightClick(event); break;
            case 11: grim30.onRightClick(event); break;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        switch (parent.mode.getValue()) {
            case 12: matrix.onStrafe(event); break;
        }
    }

    public void onDisable() {
        legit.onDisable();
    }
}
