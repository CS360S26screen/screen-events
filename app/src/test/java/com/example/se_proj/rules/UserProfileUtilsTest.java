package com.example.se_proj.rules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserProfileUtilsTest {

    @Test
    public void resolveHostId_prefersRollNumber_whenItExists() {
        assertEquals("23F-001", UserProfileUtils.resolveHostId("23F-001", "FAC-42", "uid-1"));
    }

    @Test
    public void resolveHostId_usesFacultyId_whenRollNumberIsMissing() {
        assertEquals("FAC-42", UserProfileUtils.resolveHostId(" ", "FAC-42", "uid-1"));
    }

    @Test
    public void resolveHostId_fallsBackToDocumentId_whenNoProfileIdentifiersExist() {
        assertEquals("uid-1", UserProfileUtils.resolveHostId(null, null, "uid-1"));
    }

    @Test
    public void resolveHostId_returnsEmptyString_whenEverythingIsMissing() {
        assertEquals("", UserProfileUtils.resolveHostId(null, null, null));
    }
}
