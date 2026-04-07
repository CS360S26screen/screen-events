package com.example.se_proj.rules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginInputUtilsTest {

    @Test
    public void normalizeEmail_appendsCampusDomain_whenIdentifierIsNotEmail() {
        assertEquals("abc123@campus.edu", LoginInputUtils.normalizeEmail("abc123"));
    }

    @Test
    public void normalizeEmail_preservesExistingEmail_whenInputAlreadyContainsAtSymbol() {
        assertEquals("guard@campus.edu", LoginInputUtils.normalizeEmail("guard@campus.edu"));
    }

    @Test
    public void hasCredentials_returnsFalse_whenEitherValueIsBlank() {
        assertFalse(LoginInputUtils.hasCredentials("faculty01", " "));
        assertFalse(LoginInputUtils.hasCredentials(" ", "password123"));
    }

    @Test
    public void hasCredentials_returnsTrue_whenBothValuesArePresent() {
        assertTrue(LoginInputUtils.hasCredentials("faculty01", "password123"));
    }
}
