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
 * Shared audit and reminder logic used by both admin and host flows.
 */
public final class AuditLogUtils {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private AuditLogUtils() {
    }

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

    public static boolean isOverstaying(VisitorRequest request, LocalDateTime now) {
        if (request == null || !request.getOnCampus()) {
            return false;
        }
        LocalDateTime visitEnd = parseVisitEnd(request);
        return visitEnd != null && !now.isBefore(visitEnd);
    }

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
