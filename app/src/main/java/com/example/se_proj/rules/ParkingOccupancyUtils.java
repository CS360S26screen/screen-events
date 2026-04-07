package com.example.se_proj.rules;

/**
 * Arithmetic utilities for the live parking-occupancy counter displayed on the Guard
 * and Admin dashboards.
 *
 * <p>Guards manually tap +/- buttons to track cars entering or leaving the parking
 * area. These utilities ensure the counter stays within {@code [0, maxCapacity]} even
 * if taps are slightly inaccurate (acceptance criterion: ~2–3 car margin).</p>
 */
public final class ParkingOccupancyUtils {

    private ParkingOccupancyUtils() {
    }

    /**
     * Applies a delta to the current occupancy and clamps the result to {@code [0, maxCapacity]}.
     *
     * @param currentOccupancy the current number of cars in the lot.
     * @param delta            change to apply (+1 for entry, -1 for exit).
     * @param maxCapacity      the lot's maximum capacity.
     * @return the new occupancy value, guaranteed between 0 and {@code maxCapacity}.
     */
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

    /**
     * Returns the occupancy as a ratio (0.0–1.0) for progress-bar and color-threshold logic.
     *
     * @param currentOccupancy the current number of cars.
     * @param maxCapacity      the lot's maximum capacity.
     * @return occupancy ratio, or 0 if capacity is non-positive.
     */
    public static float occupancyRatio(long currentOccupancy, long maxCapacity) {
        if (maxCapacity <= 0) {
            return 0f;
        }
        return (float) currentOccupancy / (float) maxCapacity;
    }

    /**
     * Formats the occupancy counter for display (e.g. {@code "42 / 200"}).
     *
     * @param currentOccupancy the current number of cars.
     * @param maxCapacity      the lot's maximum capacity.
     * @return formatted display string.
     */
    public static String formatCounter(long currentOccupancy, long maxCapacity) {
        return currentOccupancy + " / " + maxCapacity;
    }
}
