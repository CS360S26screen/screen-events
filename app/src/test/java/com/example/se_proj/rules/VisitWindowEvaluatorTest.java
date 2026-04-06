package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisitWindowEvaluatorTest {

    @Test
    public void evaluate_returnsAuthorized_whenCurrentTimeFallsWithinVisitWindow() {
        VisitWindowEvaluator.Decision decision = VisitWindowEvaluator.evaluate(
                request(false, "07/04/2026", "10:00", "12:00"),
                LocalDate.of(2026, 4, 7),
                LocalTime.of(10, 30)
        );

        assertEquals(VisitWindowEvaluator.VisitWindowState.AUTHORIZED, decision.getState());
        assertEquals("Check-In", decision.getActionText());
        assertTrue(decision.isActionEnabled());
        assertFalse(decision.isOverrideVisible());
    }

    @Test
    public void evaluate_returnsTooEarly_whenArrivalPrecedesStartTime() {
        VisitWindowEvaluator.Decision decision = VisitWindowEvaluator.evaluate(
                request(false, "07/04/2026", "10:00", "12:00"),
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 45)
        );

        assertEquals(VisitWindowEvaluator.VisitWindowState.TOO_EARLY, decision.getState());
        assertEquals("Early Arrival", decision.getDeniedReason());
        assertFalse(decision.isActionEnabled());
        assertTrue(decision.isOverrideVisible());
    }

    @Test
    public void evaluate_returnsExpired_whenCurrentTimeIsAtOrPastEndTime() {
        VisitWindowEvaluator.Decision decision = VisitWindowEvaluator.evaluate(
                request(false, "07/04/2026", "10:00", "12:00"),
                LocalDate.of(2026, 4, 7),
                LocalTime.of(12, 0)
        );

        assertEquals(VisitWindowEvaluator.VisitWindowState.EXPIRED, decision.getState());
        assertEquals("Expired Window", decision.getDeniedReason());
    }

    @Test
    public void evaluate_returnsWrongDate_whenVisitBelongsToAnotherDay() {
        VisitWindowEvaluator.Decision decision = VisitWindowEvaluator.evaluate(
                request(false, "08/04/2026", "10:00", "12:00"),
                LocalDate.of(2026, 4, 7),
                LocalTime.of(10, 30)
        );

        assertEquals(VisitWindowEvaluator.VisitWindowState.WRONG_DATE, decision.getState());
        assertEquals("Wrong Date", decision.getDeniedReason());
    }

    @Test
    public void evaluate_returnsInside_whenGuestIsAlreadyCheckedIn() {
        VisitWindowEvaluator.Decision decision = VisitWindowEvaluator.evaluate(
                request(true, "07/04/2026", "10:00", "12:00"),
                LocalDate.of(2026, 4, 7),
                LocalTime.of(10, 30)
        );

        assertEquals(VisitWindowEvaluator.VisitWindowState.INSIDE, decision.getState());
        assertEquals("Check-Out", decision.getActionText());
        assertTrue(decision.isActionEnabled());
    }

    @Test
    public void evaluate_returnsInvalidWindow_whenScheduleCannotBeParsed() {
        VisitWindowEvaluator.Decision decision = VisitWindowEvaluator.evaluate(
                request(false, "07/04/2026", "bad-time", "12:00"),
                LocalDate.of(2026, 4, 7),
                LocalTime.of(10, 30)
        );

        assertEquals(VisitWindowEvaluator.VisitWindowState.INVALID_WINDOW, decision.getState());
        assertEquals("Invalid Visit Window", decision.getDeniedReason());
    }

    private static VisitorRequest request(
            boolean onCampus,
            String visitDate,
            String startTime,
            String endTime
    ) {
        return new VisitorRequest(
                "req-1",
                "Guest One",
                "3520212345671",
                "Meeting",
                visitDate,
                startTime,
                endTime,
                "FAC-1",
                "creator-1",
                "faculty",
                RequestStatus.APPROVED,
                null,
                null,
                onCampus
        );
    }
}
