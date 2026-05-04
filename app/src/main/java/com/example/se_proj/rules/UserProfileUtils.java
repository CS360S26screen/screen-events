package com.example.se_proj.rules;

/**
 * Resolves stable host identifiers from Firestore user-profile data.
 *
 * <p>User profiles may store the host's identity under different field names
 * ({@code rollNumber} for students, {@code facultyId} for faculty). This utility
 * provides a single resolution method so every Activity uses the same priority order.</p>
 */
public final class UserProfileUtils {

    private UserProfileUtils() {
    }

    /**
     * Returns the best available host identifier from a user profile document.
     *
     * <p>Priority: {@code rollNumber} &gt; {@code facultyId} &gt; {@code fallbackId}
     * (typically the Firestore document ID).</p>
     *
     * @param rollNumber the student roll number field (may be {@code null} or blank).
     * @param facultyId  the faculty ID field (may be {@code null} or blank).
     * @param fallbackId fallback value if neither field is populated.
     * @return a trimmed, non-null host identifier.
     */
    public static String resolveHostId(String rollNumber, String facultyId, String fallbackId) {
        if (isNotBlank(rollNumber)) {
            return rollNumber.trim();
        }
        if (isNotBlank(facultyId)) {
            return facultyId.trim();
        }
        return fallbackId == null ? "" : fallbackId.trim();
    }

    /**
     * Returns the best host identifier, using the email local-part before falling back
     * to the document ID. This handles UID-keyed profile documents that do not store
     * an explicit rollNumber/facultyId field.
     */
    public static String resolveHostId(String rollNumber, String facultyId,
                                       String fallbackId, String email) {
        String resolved = resolveHostId(rollNumber, facultyId, "");
        if (isNotBlank(resolved)) {
            return resolved;
        }
        if (isNotBlank(email) && email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@')).trim();
            if (!localPart.isEmpty()) {
                return localPart;
            }
        }
        return fallbackId == null ? "" : fallbackId.trim();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
