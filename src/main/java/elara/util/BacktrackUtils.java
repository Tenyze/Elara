package elara.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BacktrackUtils - ported from Lizz (LiquidBounce) client.
 * Provides utility functions for backtracking entity positions
 * and simulating entity positions for rotation/attack calculations.
 */
public class BacktrackUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Legacy backtrack data (per-player position history)
    private static final ConcurrentHashMap<UUID, List<BacktrackData>> backtrackedPlayer = new ConcurrentHashMap<>();
    private static final int MAX_CACHED_POSITIONS = 10;
    private static final long MAX_AGE_MS = 2000L;

    /**
     * Record a new position for an entity (Legacy mode).
     */
    public static void addBacktrackData(UUID id, double x, double y, double z, long time) {
        List<BacktrackData> data = backtrackedPlayer.computeIfAbsent(id, k -> new ArrayList<>());
        if (data.size() >= MAX_CACHED_POSITIONS) {
            data.remove(0);
        }
        data.add(new BacktrackData(x, y, z, time));
    }

    /**
     * Get the nearest tracked distance of an entity (Legacy mode).
     * Temporarily moves the entity to each recorded position to find the closest.
     */
    public static double getNearestTrackedDistance(Entity entity) {
        if (!(entity instanceof EntityPlayer)) {
            return mc.thePlayer.getDistanceToEntity(entity);
        }

        List<BacktrackData> data = backtrackedPlayer.get(entity.getUniqueID());
        if (data == null || data.isEmpty()) {
            return mc.thePlayer.getDistanceToEntity(entity);
        }

        // Clean old data
        long now = System.currentTimeMillis();
        data.removeIf(d -> now - d.time > MAX_AGE_MS);
        if (data.isEmpty()) {
            return mc.thePlayer.getDistanceToEntity(entity);
        }

        double nearest = Double.MAX_VALUE;
        Vec3 currPos = entity.getPositionVector();
        Vec3 prevPos = new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);

        for (BacktrackData d : data) {
            // Temporarily set entity position for distance calculation
            entity.setPosition(d.x, d.y, d.z);
            double dist = mc.thePlayer.getDistanceToEntity(entity);
            if (dist < nearest) {
                nearest = dist;
            }
        }

        // Restore position
        entity.setPosition(currPos.xCoord, currPos.yCoord, currPos.zCoord);
        entity.prevPosX = prevPos.xCoord;
        entity.prevPosY = prevPos.yCoord;
        entity.prevPosZ = prevPos.zCoord;

        return nearest == Double.MAX_VALUE ? mc.thePlayer.getDistanceToEntity(entity) : nearest;
    }

    /**
     * Run a function with the entity's position temporarily set to its nearest
     * tracked position (Legacy mode).
     */
    public static <T> T runWithNearestTrackedDistance(Entity entity, RunnableProvider<T> f) {
        if (!(entity instanceof EntityPlayer)) {
            return f.run();
        }

        List<BacktrackData> data = backtrackedPlayer.get(entity.getUniqueID());
        if (data == null || data.isEmpty()) {
            return f.run();
        }

        long now = System.currentTimeMillis();
        data.removeIf(d -> now - d.time > MAX_AGE_MS);
        if (data.isEmpty()) {
            return f.run();
        }

        // Sort by distance to find nearest
        Vec3 currPos = entity.getPositionVector();
        Vec3 prevPos = new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);

        data.sort(Comparator.comparingDouble(d -> {
            entity.setPosition(d.x, d.y, d.z);
            double dist = mc.thePlayer.getDistanceToEntity(entity);
            entity.setPosition(currPos.xCoord, currPos.yCoord, currPos.zCoord);
            return dist;
        }));

        BacktrackData nearest = data.get(0);

        // Run with nearest position
        entity.setPosition(nearest.x, nearest.y, nearest.z);
        T result = f.run();

        // Restore
        entity.setPosition(currPos.xCoord, currPos.yCoord, currPos.zCoord);
        entity.prevPosX = prevPos.xCoord;
        entity.prevPosY = prevPos.yCoord;
        entity.prevPosZ = prevPos.zCoord;

        return result;
    }

    /**
     * Run a function with a simulated entity position.
     */
    public static <T> T runWithSimulatedPosition(Entity entity, Vec3 simPos, RunnableProvider<T> f) {
        Vec3 currPos = entity.getPositionVector();
        Vec3 prevPos = new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);

        entity.setPosition(simPos.xCoord, simPos.yCoord, simPos.zCoord);
        T result = f.run();

        entity.setPosition(currPos.xCoord, currPos.yCoord, currPos.zCoord);
        entity.prevPosX = prevPos.xCoord;
        entity.prevPosY = prevPos.yCoord;
        entity.prevPosZ = prevPos.zCoord;

        return result;
    }

    /**
     * Loop through backtrack data and execute an action. Returns true if action returned true.
     */
    public static boolean loopThroughBacktrackData(Entity entity, ActionProvider action) {
        if (!(entity instanceof EntityPlayer)) return false;

        List<BacktrackData> data = backtrackedPlayer.get(entity.getUniqueID());
        if (data == null || data.isEmpty()) return false;

        long now = System.currentTimeMillis();
        data.removeIf(d -> now - d.time > MAX_AGE_MS);
        if (data.isEmpty()) return false;

        Vec3 currPos = entity.getPositionVector();
        Vec3 prevPos = new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);

        boolean result = false;
        for (int i = data.size() - 1; i >= 0; i--) {
            BacktrackData d = data.get(i);
            entity.setPosition(d.x, d.y, d.z);
            if (action.run()) {
                result = true;
                break;
            }
        }

        entity.setPosition(currPos.xCoord, currPos.yCoord, currPos.zCoord);
        entity.prevPosX = prevPos.xCoord;
        entity.prevPosY = prevPos.yCoord;
        entity.prevPosZ = prevPos.zCoord;

        return result;
    }

    /**
     * Clear all backtrack data.
     */
    public static void clearAll() {
        backtrackedPlayer.clear();
    }

    /**
     * Predict client movement for a number of ticks ahead.
     * Simplified version that doesn't require full SimulatedPlayer.
     */
    public static Vec3 predictClientMovement(EntityLivingBase player, int ticksAhead) {
        if (ticksAhead <= 0) return new Vec3(player.posX, player.posY, player.posZ);

        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        double motionX = player.motionX;
        double motionY = player.motionY;
        double motionZ = player.motionZ;

        for (int i = 0; i < ticksAhead; i++) {
            x += motionX;
            y += motionY;
            z += motionZ;

            // Simplified gravity
            if (!player.onGround) {
                motionY -= 0.08;
                motionY *= 0.98;
            }

            // Simplified friction
            motionX *= 0.91;
            motionZ *= 0.91;
        }

        return new Vec3(x, y, z);
    }

    // ---- Interfaces ----

    @FunctionalInterface
    public interface RunnableProvider<T> {
        T run();
    }

    @FunctionalInterface
    public interface ActionProvider {
        boolean run();
    }

    // ---- Data class ----

    static class BacktrackData {
        final double x, y, z;
        final long time;

        BacktrackData(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }
}