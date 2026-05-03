package com.example.se_proj.rules;

import com.example.se_proj.models.WingAccessPermission;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WingAccessUtilsTest {

    // -------------------------------------------------------------------------
    // Helper factory
    // -------------------------------------------------------------------------

    private static WingAccessPermission perm(boolean active, boolean lifetime, String expiry) {
        WingAccessPermission p = new WingAccessPermission();
        p.setActive(active);
        p.setLifetime(lifetime);
        p.setExpiryDate(expiry);
        return p;
    }

    private static Calendar today(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 12, 0, 0); // month is 0-based in Calendar
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    // -------------------------------------------------------------------------
    // isPermissionValid — lifetime
    // -------------------------------------------------------------------------

    @Test
    public void isPermissionValid_trueForLifetimeActive() {
        assertTrue(WingAccessPermissionUtils.isPermissionValid(
                perm(true, true, null), Calendar.getInstance()));
    }

    @Test
    public void isPermissionValid_falseForLifetimeButInactive() {
        assertFalse(WingAccessPermissionUtils.isPermissionValid(
                perm(false, true, null), Calendar.getInstance()));
    }

    // -------------------------------------------------------------------------
    // isPermissionValid — expiry date
    // -------------------------------------------------------------------------

    @Test
    public void isPermissionValid_trueWhenExpiryIsToday() {
        Calendar today = today(2026, 5, 10);
        WingAccessPermission p = perm(true, false, "10/05/2026");
        assertTrue(WingAccessPermissionUtils.isPermissionValid(p, today));
    }

    @Test
    public void isPermissionValid_trueWhenExpiryIsFuture() {
        Calendar today = today(2026, 5, 10);
        WingAccessPermission p = perm(true, false, "31/12/2026");
        assertTrue(WingAccessPermissionUtils.isPermissionValid(p, today));
    }

    @Test
    public void isPermissionValid_falseWhenExpiryIsYesterday() {
        Calendar today = today(2026, 5, 10);
        WingAccessPermission p = perm(true, false, "09/05/2026");
        assertFalse(WingAccessPermissionUtils.isPermissionValid(p, today));
    }

    @Test
    public void isPermissionValid_falseWhenExpiryDateIsMissing() {
        assertFalse(WingAccessPermissionUtils.isPermissionValid(
                perm(true, false, null), Calendar.getInstance()));
        assertFalse(WingAccessPermissionUtils.isPermissionValid(
                perm(true, false, ""), Calendar.getInstance()));
    }

    @Test
    public void isPermissionValid_falseWhenExpiryDateIsMalformed() {
        assertFalse(WingAccessPermissionUtils.isPermissionValid(
                perm(true, false, "not-a-date"), Calendar.getInstance()));
    }

    @Test
    public void isPermissionValid_falseWhenInactiveEvenWithFutureDate() {
        Calendar today = today(2026, 5, 10);
        WingAccessPermission p = perm(false, false, "31/12/2030");
        assertFalse(WingAccessPermissionUtils.isPermissionValid(p, today));
    }

    // -------------------------------------------------------------------------
    // getDenialReason
    // -------------------------------------------------------------------------

    @Test
    public void getDenialReason_emptyForValidLifetime() {
        assertEquals("",
                WingAccessPermissionUtils.getDenialReason(
                        perm(true, true, null), Calendar.getInstance()));
    }

    @Test
    public void getDenialReason_revokedForInactivePermission() {
        assertEquals("Permission revoked",
                WingAccessPermissionUtils.getDenialReason(
                        perm(false, false, "31/12/2030"), Calendar.getInstance()));
    }

    @Test
    public void getDenialReason_expiredForPastDate() {
        Calendar today = today(2026, 5, 10);
        assertEquals("Expired",
                WingAccessPermissionUtils.getDenialReason(
                        perm(true, false, "01/01/2026"), today));
    }

    @Test
    public void getDenialReason_noExpiryDateSet_whenMissingForNonLifetime() {
        assertEquals("No expiry date set",
                WingAccessPermissionUtils.getDenialReason(
                        perm(true, false, ""), Calendar.getInstance()));
    }

    // -------------------------------------------------------------------------
    // WingConstants
    // -------------------------------------------------------------------------

    @Test
    public void wingConstants_allWingsContainsFiveEntries() {
        assertEquals(5, WingConstants.ALL_WINGS.length);
    }

    @Test
    public void wingConstants_allWingsAreNonEmpty() {
        for (String wing : WingConstants.ALL_WINGS) {
            assertFalse("Expected non-empty wing name", wing.isEmpty());
        }
    }
}
