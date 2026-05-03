package com.example.se_proj.rules;

/**
 * Canonical wing names for the wing/lab access control system.
 *
 * <p>Use these constants wherever wing identifiers are written to or compared against
 * Firestore documents to avoid typo-driven mismatches.</p>
 */
public final class WingConstants {

    private WingConstants() {}

    public static final String CS_WING = "CS Wing";
    public static final String LIFE_SCIENCES_WING = "Life Sciences Wing";
    public static final String CHEMISTRY_WING = "Chemistry Wing";
    public static final String PHYSICS_WING = "Physics Wing";
    public static final String MATHS_WING = "Maths Wing";

    /** Ordered list of all wings, suitable for populating a spinner. */
    public static final String[] ALL_WINGS = {
            CS_WING,
            LIFE_SCIENCES_WING,
            CHEMISTRY_WING,
            PHYSICS_WING,
            MATHS_WING
    };
}
