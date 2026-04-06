package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Evaluates whether a visitor can enter based on the approved visit window.
 */
public final class VisitWindowEvaluator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private VisitWindowEvaluator() {
    }

    public static Decision evaluate(
            VisitorRequest request,
            LocalDate currentDate,
            LocalTime currentTime
    ) {
        if (request.getOnCampus()) {
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

    public enum VisitWindowState {
        INSIDE,
        AUTHORIZED,
        WRONG_DATE,
        TOO_EARLY,
        EXPIRED,
        INVALID_WINDOW
    }

    public static final class Decision {
        private final VisitWindowState state;
        private final String label;
        private final String message;
        private final String actionText;
        private final boolean actionEnabled;
        private final boolean overrideVisible;
        private final String deniedReason;

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

        public VisitWindowState getState() {
            return state;
        }

        public String getLabel() {
            return label;
        }

        public String getMessage() {
            return message;
        }

        public String getActionText() {
            return actionText;
        }

        public boolean isActionEnabled() {
            return actionEnabled;
        }

        public boolean isOverrideVisible() {
            return overrideVisible;
        }

        public String getDeniedReason() {
            return deniedReason;
        }

        public boolean shouldLogDeniedAccess() {
            return !deniedReason.isEmpty();
        }
    }
}
