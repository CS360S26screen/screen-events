package com.example.se_proj.rules;

/**
 * Resolves stable host identifiers from user profile data.
 */
public final class UserProfileUtils {

    private UserProfileUtils() {
    }

    public static String resolveHostId(String rollNumber, String facultyId, String fallbackId) {
        if (isNotBlank(rollNumber)) {
            return rollNumber.trim();
        }
        if (isNotBlank(facultyId)) {
            return facultyId.trim();
        }
        return fallbackId == null ? "" : fallbackId.trim();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
