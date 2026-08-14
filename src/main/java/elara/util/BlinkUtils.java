package elara.util;

import elara.events.PacketEvent;
import elara.event.EventAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

public class BlinkUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean blinking = false;
    private static final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private static final Set<Class<? extends Packet<?>>> blacklistedPackets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Class<? extends Packet<?>>, Predicate<Packet<?>>> cancelReturnPredicates = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Packet<?>>, Runnable> cancelActions = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Packet<?>>, Runnable> releaseActions = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Packet<?>>, Predicate<Packet<?>>> releaseReturnPredicates = new ConcurrentHashMap<>();
    private static int c03Count = 0;
    private static int c02Count = 0;

    public static boolean isBlinking() {
        return blinking;
    }

    @SafeVarargs
    public static void blink(Class<? extends Packet<?>>... packetClasses) {
        if (blinking) return;
        blinking = true;
        blacklistedPackets.clear();
        cancelReturnPredicates.clear();
        cancelActions.clear();
        releaseActions.clear();
        releaseReturnPredicates.clear();
        c03Count = 0;
        c02Count = 0;
        for (Class<? extends Packet<?>> clazz : packetClasses) {
            blacklistedPackets.add(clazz);
        }
    }

    public static void stopBlink() {
        if (!blinking) return;
        blinking = false;
        flushPackets();
        blacklistedPackets.clear();
        cancelReturnPredicates.clear();
        cancelActions.clear();
        releaseActions.clear();
        releaseReturnPredicates.clear();
        c03Count = 0;
        c02Count = 0;
    }

    public static void unblink() {
        stopBlink();
    }

    public static void flushPackets() {
        while (!packetQueue.isEmpty()) {
            Packet<?> packet = packetQueue.poll();
            if (packet != null) {
                PacketUtil.sendPacket(packet);
            }
        }
    }

    public static void releasePacket(boolean send) {
        if (!blinking) return;
        
        Iterator<Packet<?>> iterator = packetQueue.iterator();
        while (iterator.hasNext()) {
            Packet<?> packet = iterator.next();
            boolean shouldRelease = true;
            
            for (Map.Entry<Class<? extends Packet<?>>, Predicate<Packet<?>>> entry : releaseReturnPredicates.entrySet()) {
                if (entry.getKey().isInstance(packet)) {
                    shouldRelease = entry.getValue().test(packet);
                    break;
                }
            }
            
            if (shouldRelease) {
                iterator.remove();
                if (send) {
                    PacketUtil.sendPacket(packet);
                }
                
                for (Map.Entry<Class<? extends Packet<?>>, Runnable> entry : releaseActions.entrySet()) {
                    if (entry.getKey().isInstance(packet)) {
                        entry.getValue().run();
                    }
                }
            }
        }
    }

    public static void addPacket(Packet<?> packet) {
        if (!blinking) return;
        
        boolean shouldCancel = false;
        for (Class<? extends Packet<?>> clazz : blacklistedPackets) {
            if (clazz.isInstance(packet)) {
                shouldCancel = true;
                break;
            }
        }
        
        if (!shouldCancel) {
            for (Map.Entry<Class<? extends Packet<?>>, Predicate<Packet<?>>> entry : cancelReturnPredicates.entrySet()) {
                if (entry.getKey().isInstance(packet)) {
                    if (!entry.getValue().test(packet)) {
                        shouldCancel = true;
                    }
                    break;
                }
            }
        }
        
        if (!shouldCancel) {
            packetQueue.add(packet);
            
            for (Map.Entry<Class<? extends Packet<?>>, Runnable> entry : cancelActions.entrySet()) {
                if (entry.getKey().isInstance(packet)) {
                    entry.getValue().run();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void setCancelReturnPredicate(Class<? extends Packet<?>> clazz, Predicate<?> predicate) {
        cancelReturnPredicates.put(clazz, (Predicate<Packet<?>>) predicate);
    }

    public static void setCancelAction(Class<? extends Packet<?>> clazz, Runnable action) {
        cancelActions.put(clazz, action);
    }

    public static void setReleaseAction(Class<? extends Packet<?>> clazz, Runnable action) {
        releaseActions.put(clazz, action);
    }

    @SuppressWarnings("unchecked")
    public static void setReleaseReturnPredicate(Class<? extends Packet<?>> clazz, Predicate<?> predicate) {
        releaseReturnPredicates.put(clazz, (Predicate<Packet<?>>) predicate);
    }

    public static void resetBlackList() {
        blacklistedPackets.clear();
    }

    public static void setC03Counter(int count) {
        c03Count = count;
    }

    public static int getC03Count() {
        return c03Count;
    }

    public static void incrementC03() {
        c03Count++;
    }

    public static void decrementC03() {
        c03Count--;
    }

    public static void setC02Counter(int count) {
        c02Count = count;
    }

    public static int getC02Count() {
        return c02Count;
    }

    public static void incrementC02() {
        c02Count++;
    }

    public static void decrementC02() {
        c02Count--;
    }

    public static void blinkPacket(PacketEvent event) {
        if (!blinking) return;
        
        Packet<?> packet = event.getPacket();
        boolean shouldCancel = false;
        
        for (Class<? extends Packet<?>> clazz : blacklistedPackets) {
            if (clazz.isInstance(packet)) {
                shouldCancel = true;
                break;
            }
        }
        
        if (!shouldCancel) {
            for (Map.Entry<Class<? extends Packet<?>>, Predicate<Packet<?>>> entry : cancelReturnPredicates.entrySet()) {
                if (entry.getKey().isInstance(packet)) {
                    if (!entry.getValue().test(packet)) {
                        shouldCancel = true;
                    }
                    break;
                }
            }
        }
        
        if (shouldCancel) {
            event.setCancelled(true);
            packetQueue.add(packet);
            
            for (Map.Entry<Class<? extends Packet<?>>, Runnable> entry : cancelActions.entrySet()) {
                if (entry.getKey().isInstance(packet)) {
                    entry.getValue().run();
                }
            }
        }
    }
}