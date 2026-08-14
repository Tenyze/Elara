package elara.util;

import java.util.Random;

public class MathUtil {
    private static final Random random = new Random();

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    public static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    public static double randomRange(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    public static float randomRange(float min, float max) {
        return min + (max - min) * random.nextFloat();
    }

    public static int randomRange(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public static double randomGaussian(double mean, double stdDev) {
        return random.nextGaussian() * stdDev + mean;
    }

    public static double roundToPlace(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double distance2D(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double angle(double x1, double z1, double x2, double z2) {
        return Math.atan2(z2 - z1, x2 - x1) * 180 / Math.PI;
    }

    public static double wrapAngleTo180(double angle) {
        angle %= 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    public static float wrapAngleTo180(float angle) {
        angle %= 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    public static double getAngleDifference(double angle1, double angle2) {
        return wrapAngleTo180(angle1 - angle2);
    }

    public static double degToRad(double degrees) {
        return degrees * Math.PI / 180;
    }

    public static double radToDeg(double radians) {
        return radians * 180 / Math.PI;
    }

    public static double sin(double degrees) {
        return Math.sin(degToRad(degrees));
    }

    public static double cos(double degrees) {
        return Math.cos(degToRad(degrees));
    }

    public static double tan(double degrees) {
        return Math.tan(degToRad(degrees));
    }

    public static double asin(double value) {
        return radToDeg(Math.asin(clamp(value, -1, 1)));
    }

    public static double acos(double value) {
        return radToDeg(Math.acos(clamp(value, -1, 1)));
    }

    public static double atan(double value) {
        return radToDeg(Math.atan(value));
    }

    public static double atan2(double y, double x) {
        return radToDeg(Math.atan2(y, x));
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int ceiling(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    public static double abs(double value) {
        return Math.abs(value);
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static int abs(int value) {
        return Math.abs(value);
    }

    public static double min(double... values) {
        double min = values[0];
        for (double value : values) {
            if (value < min) min = value;
        }
        return min;
    }

    public static double max(double... values) {
        double max = values[0];
        for (double value : values) {
            if (value > max) max = value;
        }
        return max;
    }

    public static int min(int... values) {
        int min = values[0];
        for (int value : values) {
            if (value < min) min = value;
        }
        return min;
    }

    public static int max(int... values) {
        int max = values[0];
        for (int value : values) {
            if (value > max) max = value;
        }
        return max;
    }

    public static float min(float... values) {
        float min = values[0];
        for (float value : values) {
            if (value < min) min = value;
        }
        return min;
    }

    public static float max(float... values) {
        float max = values[0];
        for (float value : values) {
            if (value > max) max = value;
        }
        return max;
    }

    public static double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    public static double denormalize(double normalized, double min, double max) {
        return normalized * (max - min) + min;
    }

    public static boolean isBetween(double value, double min, double max) {
        return value >= min && value <= max;
    }

    public static boolean isBetween(int value, int min, int max) {
        return value >= min && value <= max;
    }

    public static boolean isBetween(float value, float min, float max) {
        return value >= min && value <= max;
    }

    public static double lerpAngle(double start, double end, double progress) {
        double difference = getAngleDifference(end, start);
        return wrapAngleTo180(start + difference * progress);
    }

    public static float lerpAngle(float start, float end, float progress) {
        float difference = (float) getAngleDifference(end, start);
        return wrapAngleTo180(start + difference * progress);
    }

    public static double nextGaussianClamped(double mean, double stdDev, double min, double max) {
        double value;
        do {
            value = randomGaussian(mean, stdDev);
        } while (value < min || value > max);
        return value;
    }

    public static int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public static boolean nextBoolean() {
        return random.nextBoolean();
    }

    public static double nextDouble() {
        return random.nextDouble();
    }

    public static float nextFloat() {
        return random.nextFloat();
    }

    public static void setSeed(long seed) {
        random.setSeed(seed);
    }

    public static long nextLong() {
        return random.nextLong();
    }
}