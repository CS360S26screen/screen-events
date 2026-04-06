package com.example.se_proj.rules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParkingOccupancyUtilsTest {

    @Test
    public void clampOccupancy_preventsNegativeCounts() {
        assertEquals(0L, ParkingOccupancyUtils.clampOccupancy(0L, -1L, 200L));
    }

    @Test
    public void clampOccupancy_preventsCountsAboveConfiguredCapacity() {
        assertEquals(200L, ParkingOccupancyUtils.clampOccupancy(199L, 5L, 200L));
    }

    @Test
    public void occupancyRatio_returnsZero_whenCapacityIsNotPositive() {
        assertEquals(0f, ParkingOccupancyUtils.occupancyRatio(10L, 0L), 0.0001f);
    }

    @Test
    public void formatCounter_usesReadableGuardDashboardFormat() {
        assertEquals("12 / 200", ParkingOccupancyUtils.formatCounter(12L, 200L));
    }
}
