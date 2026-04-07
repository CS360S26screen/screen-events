package com.example.se_proj.rules;

import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.VisitorRequest;
import com.google.firebase.Timestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared audit-log and overstay-detection logic used by both the Admin Audit screen
 * and the Faculty host-reminder flow.
 *
 * <p>This utility class keeps audit concerns (log merging, overstay evaluation,
 * exit-reminder timing) in pure Java so they can be unit-tested without Android
 * dependencies. It follows the same <b>Rules Engine</b> extraction pattern as
 * {@link RequestValidationUtils} and {@link VisitWindowEvaluator}.</p>
 */
public final class AuditLogUtils {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private AuditLogUtils() {
    }

    /**
     * Merges two audit-log lists, removes duplicates (by document ID or composite key),
     * and returns the result sorted newest-first.
     *
     * <p>Used by the admin search flow which fires two parallel Firestore queries
     * (by CNIC and by host ID) and needs to combine the results without duplicates.</p>
     *
     * @param first  first batch of audit logs (may be {@code null}).
     * @param second second batch of audit logs (may be {@code null}).
     * @return merged, de-duplicated, timestamp-descending list.
     */
    public static List<AuditLog> mergeAndSortDistinct(
            List<AuditLog> first,
            List<AuditLog> second
    ) {
        Map<String, AuditLog> merged = new LinkedHashMap<>();
        addDistinctLogs(merged, first);
        addDistinctLogs(merged, second);

        List<AuditLog> results = new ArrayList<>(merged.values());
        results.sort(Comparator.comparing(AuditLogUtils::timestampOrEpoch).reversed());
        return results;
    }

    /**
     * Returns {@code true} if the visitor is on campus and their approved end time has passed.
     *
     * @param request the visitor request to evaluate.
     * @param now     the current date-time.
     * @return whether the visitor is overstaying.
     */
    public static boolean isOverstaying(VisitorRequest request, LocalDateTime now) {
        if (request == null || !request.getOnCampus()) {
            return false;
        }
        LocalDateTime visitEnd = parseVisitEnd(request);
        return visitEnd != null && !now.isBefore(visitEnd);
    }

    /**
     * Returns {@code true} if an exit reminder should be shown to the host because the
     * visitor's approved window is about to close.
     *
     * @param request         the on-campus visitor request.
     * @param now             the current date-time.
     * @param reminderMinutes how many minutes before the end time to trigger the reminder.
     * @return whether the reminder window is active (0 to {@code reminderMinutes} remaining).
     */
    public static boolean shouldSendExitReminder(
            VisitorRequest request,
            LocalDateTime now,
            long reminderMinutes
    ) {
        if (request == null || !request.getOnCampus()) {
            return false;
        }
        LocalDateTime visitEnd = parseVisitEnd(request);
        if (visitEnd == null || now.toLocalDate().isAfter(visitEnd.toLocalDate())) {
            return false;
        }
        long minutesLeft = java.time.Duration.between(now, visitEnd).toMinutes();
        return minutesLeft >= 0 && minutesLeft <= reminderMinutes;
    }

    /**
     * Converts an overstaying {@link VisitorRequest} into an {@link AuditLog} entry
     * suitable for display in the admin overstay view.
     *
     * @param request the on-campus request whose end time has passed.
     * @return an audit-log representation with action {@code "OVERSTAYING"}.
     */
    public static AuditLog toOverstayAuditLog(VisitorRequest request) {
        return new AuditLog(
                "",
                request.getGuestName(),
                request.getGuestCNIC(),
                request.getHostId(),
                "OVERSTAYING",
                "Scheduled exit: " + request.getEndTime(),
                request.getCreatorId(),
                Timestamp.now()
        );
    }

    private static void addDistinctLogs(Map<String, AuditLog> merged, List<AuditLog> logs) {
        if (logs == null) {
            return;
        }
        for (AuditLog log : logs) {
            if (log == null) {
                continue;
            }
            merged.putIfAbsent(buildDistinctKey(log), log);
        }
    }

    private static String buildDistinctKey(AuditLog log) {
        if (log.getId() != null && !log.getId().trim().isEmpty()) {
            return log.getId();
        }
        return log.getVisitorCNIC()
                + "|"
                + log.getHostId()
                + "|"
                + log.getAction()
                + "|"
                + timestampOrEpoch(log).toString();
    }

    private static LocalDateTime timestampOrEpoch(AuditLog log) {
        Timestamp timestamp = log.getTimestamp();
        return timestamp == null
                ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : LocalDateTime.ofInstant(timestamp.toDate().toInstant(), java.time.ZoneId.systemDefault());
    }

    private static LocalDateTime parseVisitEnd(VisitorRequest request) {
        try {
            LocalDate date = LocalDate.parse(request.getVisitDate(), DATE_FORMAT);
            LocalTime time = LocalTime.parse(request.getEndTime(), TIME_FORMAT);
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
