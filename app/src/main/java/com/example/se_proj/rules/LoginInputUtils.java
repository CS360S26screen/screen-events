package com.example.se_proj.rules;

/**
 * Keeps login input normalization out of the Activity so it can be tested.
 */
public final class LoginInputUtils {

    private LoginInputUtils() {
    }

    public static boolean hasCredentials(String userId, String password) {
        return userId != null
                && !userId.trim().isEmpty()
                && password != null
                && !password.trim().isEmpty();
    }

    public static String normalizeEmail(String userId) {
        if (userId == null) {
            return "";
        }
        String trimmed = userId.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.contains("@") ? trimmed : trimmed + "@campus.edu";
    }
}
