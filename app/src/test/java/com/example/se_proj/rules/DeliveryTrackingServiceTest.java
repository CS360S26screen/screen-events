package com.example.se_proj.rules;

import com.example.se_proj.models.DeliveryLog;
import com.google.firebase.Timestamp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeliveryTrackingServiceTest {

    @Test
    public void calculateDuration_returnsWholeMinutesBetweenEntryAndExit() {
        DeliveryTrackingService service = new DeliveryTrackingService(null, "guard-1", null);

        long duration = service.calculateDuration(
                new Timestamp(1000L, 0),
                new Timestamp(1000L + 42L * 60L, 0)
        );

        assertEquals(42L, duration);
    }

    @Test
    public void isOverstaying_returnsTrueOnlyForActiveDeliveriesPastExpectedExit() {
        DeliveryTrackingService service = new DeliveryTrackingService(null, "guard-1", null);
        DeliveryLog active = delivery(DeliveryLog.STATUS_ACTIVE);
        DeliveryLog completed = delivery(DeliveryLog.STATUS_COMPLETED);

        assertTrue(service.isOverstaying(active, new Timestamp(2000L, 0)));
        assertFalse(service.isOverstaying(completed, new Timestamp(2000L, 0)));
        assertFalse(service.isOverstaying(active, new Timestamp(1500L, 0)));
    }

    private static DeliveryLog delivery(String status) {
        DeliveryLog log = new DeliveryLog(
                "3520212345671",
                "Rider One",
                "3520212345671",
                "FoodExpress",
                "LEA-1234",
                "Faculty Block A",
                "FAC-102",
                "Dr. Khan",
                "in_gate",
                new Timestamp(1000L, 0),
                new Timestamp(2000L, 0),
                "guard-1"
        );
        log.setStatus(status);
        return log;
    }
}
