package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Encapsulates request validation rules so they can be unit tested independently of Android UI code.
 */
public final class RequestValidationUtils {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private RequestValidationUtils() {
    }

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

        return ValidationResult.valid();
    }

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

    public static String normalizeCnic(String cnic) {
        return cnic == null ? "" : cnic.replaceAll("[^\\d]", "");
    }

    public static boolean isValidCnic(String cnic) {
        return normalizeCnic(cnic).length() == 13;
    }

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

    public static boolean hasBlockingStudentPass(List<VisitorRequest> requests) {
        if (requests == null) {
            return false;
        }
        for (VisitorRequest request : requests) {
            if (request != null && (request.getOnCampus()
                    || RequestStatus.isDuplicateBlockingStatus(request.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    public static ValidationResult validateStudentGuestRequest(
            String guestName,
            String guestCnic,
            String visitDate,
            String startTime,
            String endTime,
            LocalDate currentDate,
            LocalTime currentTime
    ) {
        ValidationResult basic = validateScheduledRequest(guestName, guestCnic, "Student Guest Visit", visitDate, startTime, endTime, currentDate, currentTime);
        if (!basic.isValid()) return basic;

        LocalTime parsedStart = parseTime(startTime);
        LocalTime parsedEnd = parseTime(endTime);
        
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

    public static final class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
