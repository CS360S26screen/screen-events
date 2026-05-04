package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Evaluates whether a visitor can enter campus based on the approved visit window.
 *
 * <p>This class implements the <b>Strategy</b> pattern for gate-access decisions.
 * Given a {@link VisitorRequest} and the current date/time, {@link #evaluate} returns
 * a {@link Decision} that tells the Guard UI exactly what to display: status label,
 * action button text, whether an override button is needed, and whether the attempt
 * should be logged as a denied-access event.</p>
 *
 * <h3>Decision States</h3>
 * <ul>
 *   <li>{@code INSIDE} — visitor is already on campus (show Check-Out).</li>
 *   <li>{@code AUTHORIZED} — current time falls within the approved window (allow Check-In).</li>
 *   <li>{@code TOO_EARLY} — visitor arrived before the start time.</li>
 *   <li>{@code EXPIRED} — the visit window has already closed.</li>
 *   <li>{@code WRONG_DATE} — the visit is scheduled for a different day.</li>
 *   <li>{@code INVALID_WINDOW} — date/time fields could not be parsed.</li>
 * </ul>
 *
 * <p>For all denied states the supervisor-override button is shown, allowing a
 * guard to manually grant entry with full audit logging.</p>
 */
public final class VisitWindowEvaluator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private VisitWindowEvaluator() {
    }

    /**
     * Evaluates a visitor's eligibility to enter campus right now.
     *
     * @param request     the approved visitor request to evaluate.
     * @param currentDate the date to treat as "today" (injectable for tests).
     * @param currentTime the time to treat as "now" (injectable for tests).
     * @return a {@link Decision} describing the access outcome and UI directives.
     */
    public static Decision evaluate(
            VisitorRequest request,
            LocalDate currentDate,
            LocalTime currentTime
    ) {
        if (request.isOnCampus()) {
            return new Decision(
                    VisitWindowState.INSIDE,
                    "INSIDE",
                    "Currently On Campus",
                    "Check-Out",
                    true,
                    false,
                    ""
            );
        }

        LocalDate visitDate = parseDate(request.getVisitDate());
        LocalTime startTime = parseTime(request.getStartTime());
        LocalTime endTime = parseTime(request.getEndTime());

        if (visitDate == null || startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return new Decision(
                    VisitWindowState.INVALID_WINDOW,
                    "INVALID WINDOW",
                    "Visit schedule is invalid",
                    "Check-In",
                    false,
                    true,
                    "Invalid Visit Window"
            );
        }

        if (!visitDate.equals(currentDate)) {
            return new Decision(
                    VisitWindowState.WRONG_DATE,
                    "WRONG DATE",
                    "Visit scheduled for " + request.getVisitDate(),
                    "Check-In",
                    false,
                    true,
                    "Wrong Date"
            );
        }

        if (currentTime.isBefore(startTime)) {
            return new Decision(
                    VisitWindowState.TOO_EARLY,
                    "TOO EARLY",
                    "Entry allowed after " + request.getStartTime(),
                    "Check-In",
                    false,
                    true,
                    "Early Arrival"
            );
        }

        if (!currentTime.isBefore(endTime)) {
            return new Decision(
                    VisitWindowState.EXPIRED,
                    "EXPIRED",
                    "Visit window expired at " + request.getEndTime(),
                    "Check-In",
                    false,
                    true,
                    "Expired Window"
            );
        }

        return new Decision(
                VisitWindowState.AUTHORIZED,
                "AUTHORIZED",
                "Authorized for Entry",
                "Check-In",
                true,
                false,
                ""
        );
    }

    private static LocalDate parseDate(String visitDate) {
        try {
            return LocalDate.parse(visitDate, DATE_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalTime parseTime(String visitTime) {
        try {
            return LocalTime.parse(visitTime, TIME_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** Possible outcomes of a visit-window evaluation. */
    public enum VisitWindowState {
        /** Visitor is already checked in on campus. */
        INSIDE,
        /** Current time is within the approved window — entry allowed. */
        AUTHORIZED,
        /** Visit is scheduled for a different calendar day. */
        WRONG_DATE,
        /** Visitor arrived before the approved start time. */
        TOO_EARLY,
        /** The approved visit window has already closed. */
        EXPIRED,
        /** Date or time fields could not be parsed. */
        INVALID_WINDOW
    }

    /**
     * Immutable value object carrying the full gate-access decision for a single request,
     * including UI directives (label text, button states) and audit metadata.
     */
    public static final class Decision {
        private final VisitWindowState state;
        private final String label;
        private final String message;
        private final String actionText;
        private final boolean actionEnabled;
        private final boolean overrideVisible;
        private final String deniedReason;

        /**
         * Creates a complete gate-access decision.
         *
         * @param state normalized decision state.
         * @param label short status label for the guard UI.
         * @param message explanatory message shown to the guard.
         * @param actionText text for the primary action button.
         * @param actionEnabled whether the primary action should be enabled.
         * @param overrideVisible whether the supervisor override action should be visible.
         * @param deniedReason audit reason to record for denied attempts, or an empty string.
         */
        public Decision(
                VisitWindowState state,
                String label,
                String message,
                String actionText,
                boolean actionEnabled,
                boolean overrideVisible,
                String deniedReason
        ) {
            this.state = state;
            this.label = label;
            this.message = message;
            this.actionText = actionText;
            this.actionEnabled = actionEnabled;
            this.overrideVisible = overrideVisible;
            this.deniedReason = deniedReason;
        }

        /** @return the evaluated window state (e.g. AUTHORIZED, EXPIRED). */
        public VisitWindowState getState() {
            return state;
        }

        /** @return short label for the status chip (e.g. "AUTHORIZED", "TOO EARLY"). */
        public String getLabel() {
            return label;
        }

        /** @return descriptive message shown to the guard (e.g. "Entry allowed after 10:00"). */
        public String getMessage() {
            return message;
        }

        /** @return text for the primary action button ("Check-In" or "Check-Out"). */
        public String getActionText() {
            return actionText;
        }

        /** @return whether the primary action button should be enabled. */
        public boolean isActionEnabled() {
            return actionEnabled;
        }

        /** @return whether the supervisor-override button should be visible. */
        public boolean isOverrideVisible() {
            return overrideVisible;
        }

        /** @return reason string for denied-access audit log entries, or empty if not denied. */
        public String getDeniedReason() {
            return deniedReason;
        }

        /** @return {@code true} if this decision should generate a denied-access audit log. */
        public boolean shouldLogDeniedAccess() {
            return !deniedReason.isEmpty();
        }
    }
}
