package com.example.se_proj.rules;

import com.example.se_proj.models.WingAccessPermission;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Pure stateless helpers for evaluating wing access permission validity.
 *
 * <p>Extracted from {@link com.example.se_proj.WingScannerActivity} so the core logic
 * can be unit-tested without Android dependencies. All methods are static.</p>
 */
public final class WingAccessPermissionUtils {

    private WingAccessPermissionUtils() {}

    /**
     * Returns {@code true} when {@code perm} is active and either has lifetime access or has an
     * expiry date that is on or after midnight of {@code today}.
     *
     * @param perm  the permission to check (must not be null)
     * @param today Calendar instance representing the current day (time-of-day is ignored)
     */
    public static boolean isPermissionValid(WingAccessPermission perm, Calendar today) {
        if (!perm.isActive()) return false;
        if (perm.isLifetime()) return true;

        String expiryStr = perm.getExpiryDate();
        if (expiryStr == null || expiryStr.isEmpty()) return false;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            sdf.setLenient(false);
            Date expiry = sdf.parse(expiryStr);

            Calendar todayMidnight = (Calendar) today.clone();
            todayMidnight.set(Calendar.HOUR_OF_DAY, 0);
            todayMidnight.set(Calendar.MINUTE, 0);
            todayMidnight.set(Calendar.SECOND, 0);
            todayMidnight.set(Calendar.MILLISECOND, 0);

            return expiry != null && !expiry.before(todayMidnight.getTime());
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Returns a human-readable denial reason, or an empty string when {@code perm} is valid.
     *
     * @param perm  the permission to inspect
     * @param today Calendar instance representing the current day
     */
    public static String getDenialReason(WingAccessPermission perm, Calendar today) {
        if (!perm.isActive()) return "Permission revoked";
        if (perm.isLifetime()) return "";
        String expiryStr = perm.getExpiryDate();
        if (expiryStr == null || expiryStr.isEmpty()) return "No expiry date set";
        if (!isPermissionValid(perm, today)) return "Expired";
        return "";
    }
}
