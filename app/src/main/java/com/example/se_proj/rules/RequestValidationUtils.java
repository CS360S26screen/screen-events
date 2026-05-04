package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Pure validation logic for visitor access requests, decoupled from Android framework code.
 *
 * <p>This utility class implements the <b>Strategy / Rules Engine</b> pattern: each public
 * method encapsulates a single validation concern (field completeness, CNIC format, time
 * window bounds, duplicate detection, student guest-limit enforcement) so that the same
 * rules are applied consistently across all Activity screens and can be verified through
 * unit tests without an Android runtime.</p>
 *
 * <p>All visitor requests are constrained to the campus visiting hours of
 * <strong>09:00 – 22:00</strong>.</p>
 */
public final class RequestValidationUtils {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private RequestValidationUtils() {
    }

    /**
     * Convenience overload that uses the system clock for date/time checks.
     *
     * @see #validateScheduledRequest(String, String, String, String, String, String, LocalDate, LocalTime)
     */
    public static ValidationResult validateScheduledRequest(
            String guestName,
            String guestCnic,
            String purpose,
            String visitDate,
            String startTime,
            String endTime
    ) {
        return validateScheduledRequest(
                guestName,
                guestCnic,
                purpose,
                visitDate,
                startTime,
                endTime,
                LocalDate.now(),
                LocalTime.now()
        );
    }

    /**
     * Validates a pre-scheduled visitor request (faculty or student).
     *
     * <p>Checks performed in order:</p>
     * <ol>
     *   <li>All fields are non-blank.</li>
     *   <li>CNIC is exactly 13 digits.</li>
     *   <li>Visit date and times parse correctly.</li>
     *   <li>End time is after start time.</li>
     *   <li>Visit date is not in the past.</li>
     *   <li>If today, the window has not already closed.</li>
     *   <li>Start time is not before 09:00 and end time is not after 22:00.</li>
     * </ol>
     *
     * @param guestName   visitor's full name.
     * @param guestCnic   visitor's CNIC (raw or formatted).
     * @param purpose     reason for the visit.
     * @param visitDate   date in {@code dd/MM/yyyy} format.
     * @param startTime   window start in {@code HH:mm} format.
     * @param endTime     window end in {@code HH:mm} format.
     * @param currentDate the date to treat as "today" (injectable for tests).
     * @param currentTime the time to treat as "now" (injectable for tests).
     * @return a {@link ValidationResult} indicating success or the first failing rule.
     */
    public static ValidationResult validateScheduledRequest(
            String guestName,
            String guestCnic,
            String purpose,
            String visitDate,
            String startTime,
            String endTime,
            LocalDate currentDate,
            LocalTime currentTime
    ) {
        if (isBlank(guestName)
                || isBlank(guestCnic)
                || isBlank(purpose)
                || isBlank(visitDate)
                || isBlank(startTime)
                || isBlank(endTime)) {
            return ValidationResult.invalid("Please fill all fields");
        }

        if (!isValidCnic(guestCnic)) {
            return ValidationResult.invalid("Please enter a valid 13-digit CNIC");
        }

        LocalDate parsedDate = parseDate(visitDate);
        if (parsedDate == null) {
            return ValidationResult.invalid("Please select a valid visit date");
        }

        LocalTime parsedStart = parseTime(startTime);
        LocalTime parsedEnd = parseTime(endTime);
        if (parsedStart == null || parsedEnd == null) {
            return ValidationResult.invalid("Please select a valid visit time");
        }

        if (!parsedEnd.isAfter(parsedStart)) {
            return ValidationResult.invalid("End time must be after start time");
        }

        if (parsedDate.isBefore(currentDate)) {
            return ValidationResult.invalid("Visit date cannot be in the past");
        }

        if (parsedDate.equals(currentDate) && !parsedEnd.isAfter(currentTime)) {
            return ValidationResult.invalid("Visit window has already ended");
        }

        LocalTime minTime = LocalTime.of(9, 0);
        LocalTime maxTime = LocalTime.of(22, 0);

        if (parsedStart.isBefore(minTime)) {
            return ValidationResult.invalid("Visits cannot start before 9:00 AM");
        }
        if (parsedEnd.isAfter(maxTime)) {
            return ValidationResult.invalid("Visits must end by 10:00 PM");
        }

        return ValidationResult.valid();
    }

    /**
     * Validates a walk-in (ad-hoc) request created by a guard at the gate.
     *
     * <p>Walk-in requests only require basic field and CNIC checks; time window
     * validation is not needed because the visit starts immediately.</p>
     *
     * @param hostId    roll number or faculty ID of the campus host.
     * @param guestName visitor's full name.
     * @param guestCnic visitor's CNIC (raw or formatted).
     * @param purpose   reason for the visit.
     * @return a {@link ValidationResult} indicating success or the first failing rule.
     */
    public static ValidationResult validateWalkInRequest(
            String hostId,
            String guestName,
            String guestCnic,
            String purpose
    ) {
        if (isBlank(hostId) || isBlank(guestName) || isBlank(guestCnic) || isBlank(purpose)) {
            return ValidationResult.invalid("Please fill all fields");
        }

        if (!isValidCnic(guestCnic)) {
            return ValidationResult.invalid("Please enter a valid 13-digit CNIC");
        }

        return ValidationResult.valid();
    }

    /**
     * Strips all non-digit characters from a CNIC string for consistent storage and comparison.
     *
     * @param cnic raw CNIC input (e.g. {@code "35202-1234567-1"}).
     * @return digits-only CNIC, or empty string if {@code null}.
     */
    public static String normalizeCnic(String cnic) {
        return cnic == null ? "" : cnic.replaceAll("[^\\d]", "");
    }

    /**
     * Returns {@code true} if the CNIC contains exactly 13 digits after normalization.
     *
     * @param cnic raw or normalized CNIC string.
     * @return whether the CNIC is valid.
     */
    public static boolean isValidCnic(String cnic) {
        return normalizeCnic(cnic).length() == 13;
    }

    /**
     * Checks whether an existing request matches a new submission closely enough to be
     * considered a duplicate (same host, same guest CNIC, same date, and a non-terminal status).
     *
     * @param request   an existing request from Firestore.
     * @param hostId    the host ID on the new submission.
     * @param guestCnic the guest CNIC on the new submission (raw or normalized).
     * @param visitDate the visit date on the new submission.
     * @return {@code true} if this request would be a duplicate.
     */
    public static boolean matchesDuplicateCandidate(
            VisitorRequest request,
            String hostId,
            String guestCnic,
            String visitDate
    ) {
        if (request == null) {
            return false;
        }
        String normalizedRequestCnic = normalizeCnic(request.getGuestCNIC());
        String normalizedInputCnic = normalizeCnic(guestCnic);
        return hostId != null
                && hostId.equals(request.getHostId())
                && visitDate != null
                && visitDate.equals(request.getVisitDate())
                && normalizedInputCnic.equals(normalizedRequestCnic)
                && RequestStatus.isDuplicateBlockingStatus(request.getStatus());
    }

    /**
     * Blocks submission if there is a pending/pending_adhoc request or a guest currently on campus.
     * Approved requests do NOT block — use {@link #hasTimeClashWithApproved} for overlap checks.
     */
    public static boolean hasBlockingStudentPass(List<VisitorRequest> requests) {
        if (requests == null) {
            return false;
        }
        for (VisitorRequest request : requests) {
            if (request == null) continue;
            String status = RequestStatus.normalize(request.getStatus());
            if (request.isOnCampus()
                    || RequestStatus.PENDING.equals(status)
                    || RequestStatus.PENDING_ADHOC.equals(status)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the proposed time window overlaps with any already-approved
     * request on the same date. Used to allow students to submit additional requests as
     * long as their visit windows do not conflict.
     *
     * @param requests  the student's existing requests from Firestore.
     * @param visitDate the date of the new request ({@code dd/MM/yyyy}).
     * @param startTime the start time of the new request ({@code HH:mm}).
     * @param endTime   the end time of the new request ({@code HH:mm}).
     * @return {@code true} if at least one approved request overlaps the proposed window.
     */
    public static boolean hasTimeClashWithApproved(
            List<VisitorRequest> requests,
            String visitDate,
            String startTime,
            String endTime
    ) {
        if (requests == null) return false;
        LocalTime newStart = parseTime(startTime);
        LocalTime newEnd = parseTime(endTime);
        if (newStart == null || newEnd == null) return false;

        for (VisitorRequest request : requests) {
            if (request == null) continue;
            if (!RequestStatus.APPROVED.equals(RequestStatus.normalize(request.getStatus()))) continue;
            if (visitDate == null || !visitDate.equals(request.getVisitDate())) continue;

            LocalTime existStart = parseTime(request.getStartTime());
            LocalTime existEnd = parseTime(request.getEndTime());
            if (existStart == null || existEnd == null) continue;

            // Overlap: new starts before existing ends AND new ends after existing starts
            if (newStart.isBefore(existEnd) && newEnd.isAfter(existStart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates a student guest-pass request. Delegates to
     * {@link #validateScheduledRequest} with a fixed purpose string.
     * The 09:00–22:00 visiting-hours constraint is enforced by the base method.
     *
     * @param guestName   visitor's full name.
     * @param guestCnic   visitor's CNIC.
     * @param visitDate   date in {@code dd/MM/yyyy} format.
     * @param startTime   window start in {@code HH:mm} format.
     * @param endTime     window end in {@code HH:mm} format.
     * @param currentDate the date to treat as "today".
     * @param currentTime the time to treat as "now".
     * @return a {@link ValidationResult} indicating success or the first failing rule.
     */
    public static ValidationResult validateStudentGuestRequest(
            String guestName,
            String guestCnic,
            String visitDate,
            String startTime,
            String endTime,
            LocalDate currentDate,
            LocalTime currentTime
    ) {
        // 9am-10pm enforcement is now in validateScheduledRequest
        return validateScheduledRequest(guestName, guestCnic, "Student Guest Visit", visitDate, startTime, endTime, currentDate, currentTime);
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

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Immutable result of a validation check, carrying a pass/fail flag and an
     * optional user-facing error message.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        /** Factory for a successful validation. */
        public static ValidationResult valid() {
            return new ValidationResult(true, "");
        }

        /**
         * Factory for a failed validation.
         *
         * @param message user-facing description of what went wrong.
         */
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        /** @return {@code true} if all validation checks passed. */
        public boolean isValid() {
            return valid;
        }

        /** @return the error message, or empty string on success. */
        public String getMessage() {
            return message;
        }
    }
}
