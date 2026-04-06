package com.example.se_proj.rules;

/**
 * Parking counters must stay bounded so inaccurate taps do not produce negative or impossible values.
 */
public final class ParkingOccupancyUtils {

    private ParkingOccupancyUtils() {
    }

    public static long clampOccupancy(long currentOccupancy, long delta, long maxCapacity) {
        if (maxCapacity < 0) {
            maxCapacity = 0;
        }
        long nextOccupancy = currentOccupancy + delta;
        if (nextOccupancy < 0) {
            return 0;
        }
        return Math.min(nextOccupancy, maxCapacity);
    }

    public static float occupancyRatio(long currentOccupancy, long maxCapacity) {
        if (maxCapacity <= 0) {
            return 0f;
        }
        return (float) currentOccupancy / (float) maxCapacity;
    }

    public static String formatCounter(long currentOccupancy, long maxCapacity) {
        return currentOccupancy + " / " + maxCapacity;
    }
}
