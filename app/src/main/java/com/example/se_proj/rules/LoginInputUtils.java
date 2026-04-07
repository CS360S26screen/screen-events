package com.example.se_proj.rules;

/**
 * Login-form input validation and normalization, extracted from {@link com.example.se_proj.LoginActivity}
 * for unit-testability.
 *
 * <p>Handles the convention that users may enter a bare roll number / faculty ID
 * (e.g. {@code "23F-0041"}) or a full email. If no {@code @} is present the default
 * campus domain is appended automatically.</p>
 */
public final class LoginInputUtils {

    private LoginInputUtils() {
    }

    /**
     * Returns {@code true} if both the user ID and password fields are non-blank.
     *
     * @param userId   raw text from the user-ID input field.
     * @param password raw text from the password input field.
     * @return whether minimal credentials are present for an authentication attempt.
     */
    public static boolean hasCredentials(String userId, String password) {
        return userId != null
                && !userId.trim().isEmpty()
                && password != null
                && !password.trim().isEmpty();
    }

    /**
     * Converts a user ID into an email address suitable for Firebase Authentication.
     *
     * <p>If the input already contains {@code @}, it is returned as-is (trimmed).
     * Otherwise {@code @campus.edu} is appended.</p>
     *
     * @param userId raw text from the user-ID input field.
     * @return a trimmed email string, or empty string if {@code null}/blank.
     */
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
