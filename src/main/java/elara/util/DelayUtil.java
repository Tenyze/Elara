package elara.util;

import java.util.Random;

public class DelayUtil {
    private static final Random random = new Random();
    private long lastTime;

    public DelayUtil() {
        this.lastTime = System.currentTimeMillis();
    }

    public DelayUtil(long initialTime) {
        this.lastTime = initialTime;
    }

    public void reset() {
        this.lastTime = System.currentTimeMillis();
    }

    public void setLastTime(long time) {
        this.lastTime = time;
    }

    public long getLastTime() {
        return this.lastTime;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - this.lastTime;
    }

    public boolean hasPassed(long milliseconds) {
        return getElapsedTime() >= milliseconds;
    }

    public boolean hasPassed(double milliseconds) {
        return getElapsedTime() >= milliseconds;
    }

    public boolean hasPassed(int milliseconds) {
        return getElapsedTime() >= milliseconds;
    }

    public static long randomDelay(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public static long randomDelay(double min, double max) {
        return (long) (min + (max - min) * random.nextDouble());
    }

    public static long smartDelay(int min, int max) {
        double baseDelay = min + (max - min) * random.nextDouble();
        double jitter = random.nextGaussian() * (max - min) * 0.3;
        return (long) Math.max(min, Math.min(baseDelay + jitter, max * 2));
    }

    public static long humanDelay(int min, int max) {
        double baseDelay = min + (max - min) * random.nextDouble();
        double variance = random.nextGaussian() * (max - min) * 0.25;
        double result = baseDelay + variance;
        
        if (random.nextInt(100) < 5) {
            result *= (1.5 + random.nextDouble());
        }
        
        if (random.nextInt(100) < 2) {
            result *= (2.0 + random.nextDouble() * 2.0);
        }
        
        return (long) Math.max(min, Math.min(result, max * 3));
    }

    public static long burstDelay(int baseDelay, int burstCount) {
        if (burstCount <= 0) {
            return baseDelay;
        }
        return (long) (baseDelay * (0.5 + random.nextDouble() * 0.5));
    }

    public static long gradualDelay(int min, int max, int consecutiveActions) {
        double penalty = Math.min(consecutiveActions * 0.1, 0.5);
        double baseDelay = min + (max - min) * random.nextDouble();
        return (long) (baseDelay * (1 + penalty));
    }

    public static long jitterDelay(int baseDelay, double jitterPercentage) {
        double jitter = baseDelay * jitterPercentage * (random.nextDouble() * 2 - 1);
        return (long) (baseDelay + jitter);
    }

    public static long gaussianDelay(double mean, double stdDev) {
        return (long) random.nextGaussian() * (long) stdDev + (long) mean;
    }

    public static long exponentialDelay(int baseDelay, int attempts) {
        return (long) (baseDelay * Math.pow(1.5, attempts));
    }

    public static boolean shouldSkip(int skipChance) {
        return random.nextInt(100) < skipChance;
    }

    public static boolean shouldSkip(int skipChance, int consecutiveActions, int maxConsecutive) {
        if (consecutiveActions >= maxConsecutive) {
            return true;
        }
        return random.nextInt(100) < skipChance;
    }

    public static long simulateHumanReaction() {
        return 150 + random.nextInt(200);
    }

    public static long simulateTypingDelay() {
        return 50 + random.nextInt(100);
    }

    public static long simulateClickDelay() {
        return 80 + random.nextInt(120);
    }

    public static long simulateReadingDelay(int textLength) {
        double baseTime = textLength * 8;
        double variance = random.nextGaussian() * baseTime * 0.2;
        return (long) Math.max(baseTime * 0.5, baseTime + variance);
    }

    public static long getNextTickDelay(int ticks) {
        return ticks * 50L;
    }

    public static long getRandomTickDelay(int minTicks, int maxTicks) {
        return getNextTickDelay((int) randomDelay(minTicks, maxTicks));
    }

    public static boolean checkTickDelay(long lastTime, int ticks) {
        return System.currentTimeMillis() - lastTime >= ticks * 50L;
    }

    public static int ticksToMilliseconds(int ticks) {
        return ticks * 50;
    }

    public static int millisecondsToTicks(long milliseconds) {
        return (int) (milliseconds / 50);
    }

    public static long calculateDelayWithVariance(int baseDelay, int variancePercent) {
        int variance = (int) (baseDelay * variancePercent / 100.0);
        return baseDelay + randomDelay(-variance, variance);
    }

    public static long calculateHumanizedDelay(int min, int max) {
        int range = max - min;
        if (range <= 0) {
            return min;
        }

        double[] weights = {0.1, 0.2, 0.3, 0.2, 0.1, 0.05, 0.05};
        int[] segments = new int[weights.length];
        
        int segmentSize = range / weights.length;
        for (int i = 0; i < weights.length; i++) {
            segments[i] = min + i * segmentSize;
        }

        double randomValue = random.nextDouble();
        double cumulativeWeight = 0;
        int selectedSegment = 0;
        
        for (int i = 0; i < weights.length; i++) {
            cumulativeWeight += weights[i];
            if (randomValue <= cumulativeWeight) {
                selectedSegment = i;
                break;
            }
        }

        int segmentMin = segments[selectedSegment];
        int segmentMax = (selectedSegment == weights.length - 1) ? max : segments[selectedSegment + 1];
        
        return randomDelay(segmentMin, segmentMax);
    }

    public static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void sleepRandom(int min, int max) {
        sleep(randomDelay(min, max));
    }
}