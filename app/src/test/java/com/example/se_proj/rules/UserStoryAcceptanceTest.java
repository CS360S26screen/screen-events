package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance-style tests mapped to backlog user stories for role login,
 * request submission, visit timing, and conflict handling.
 *
 * <p>These tests stay at rules-engine/domain level so they are fast and deterministic,
 * while still validating business behavior expected by guard/student/faculty/admin flows.</p>
 */
public class UserStoryAcceptanceTest {

    // ---------- Login stories (guard/student/faculty/admin) ----------

    @Test
    public void login_guardCredentialsAreAccepted_andEmailIsPreserved() {
        assertTrue(LoginInputUtils.hasCredentials("guard@campus.edu", "securePass1"));
        assertEquals("guard@campus.edu", LoginInputUtils.normalizeEmail("guard@campus.edu"));
    }

    @Test
    public void login_studentBareRollIsAccepted_andCampusDomainIsAppended() {
        assertTrue(LoginInputUtils.hasCredentials("23F-0041", "securePass1"));
        assertEquals("23F-0041@campus.edu", LoginInputUtils.normalizeEmail("23F-0041"));
    }

    @Test
    public void login_facultyBareIdIsAccepted_andCampusDomainIsAppended() {
        assertTrue(LoginInputUtils.hasCredentials("FAC-102", "securePass1"));
        assertEquals("FAC-102@campus.edu", LoginInputUtils.normalizeEmail("FAC-102"));
    }

    @Test
    public void login_adminBareIdIsAccepted_andCampusDomainIsAppended() {
        assertTrue(LoginInputUtils.hasCredentials("ADMIN-1", "securePass1"));
        assertEquals("ADMIN-1@campus.edu", LoginInputUtils.normalizeEmail("ADMIN-1"));
    }

    // ---------- Request submission stories ----------

    @Test
    public void facultyScheduledRequest_validData_isAcceptedForSubmission() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Visitor Faculty",
                "3520212345671",
                "Meeting",
                "09/04/2026",
                "10:00",
                "12:00",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 0)
        );

        assertTrue(result.isValid());
    }

    @Test
    public void studentGuestPass_validData_isAcceptedForSubmission() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateStudentGuestRequest(
                "Visitor Student",
                "3520212345672",
                "09/04/2026",
                "11:00",
                "12:30",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(10, 0)
        );

        assertTrue(result.isValid());
    }

    @Test
    public void guardWalkInRequest_validData_isAcceptedForSubmission() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateWalkInRequest(
                "FAC-102",
                "Visitor WalkIn",
                "3520212345673",
                "Urgent document drop"
        );

        assertTrue(result.isValid());
    }

    @Test
    public void adminCanProcessPendingRequest_toApprovedOrRejected() {
        assertTrue(RequestStatus.canEdit(RequestStatus.PENDING));
        assertEquals(RequestStatus.APPROVED, RequestStatus.normalize("APPROVED"));
        assertEquals(RequestStatus.REJECTED, RequestStatus.normalize("REJECTED"));
    }

    // ---------- Visit timing policy stories ----------

    @Test
    public void submissionRejectsStartBeforeAllowedTime_9am() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Early Visitor",
                "3520212345674",
                "Meeting",
                "09/04/2026",
                "08:30",
                "10:00",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(8, 0)
        );

        assertFalse(result.isValid());
        assertEquals("Visits cannot start before 9:00 AM", result.getMessage());
    }

    @Test
    public void submissionRejectsEndAfterAllowedTime_10pm() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Late Visitor",
                "3520212345675",
                "Meeting",
                "09/04/2026",
                "21:30",
                "22:30",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 0)
        );

        assertFalse(result.isValid());
        assertEquals("Visits must end by 10:00 PM", result.getMessage());
    }

    @Test
    public void guardDecisionWithinWindow_isAuthorized() {
        VisitWindowEvaluator.Decision decision = VisitWindowEvaluator.evaluate(
                request("req-auth", "3520212345676", "09/04/2026", "10:00", "12:00", RequestStatus.APPROVED, false),
                LocalDate.of(2026, 4, 9),
                LocalTime.of(10, 30)
        );

        assertEquals(VisitWindowEvaluator.VisitWindowState.AUTHORIZED, decision.getState());
        assertTrue(decision.isActionEnabled());
    }

    // ---------- Conflict stories ----------

    @Test
    public void duplicateSubmissionSameHostGuestDate_isBlocked() {
        VisitorRequest existing = request(
                "req-dup",
                "35202-1234567-7",
                "09/04/2026",
                "10:00",
                "11:00",
                RequestStatus.PENDING,
                false
        );

        boolean duplicate = RequestValidationUtils.matchesDuplicateCandidate(
                existing,
                "FAC-1",
                "3520212345677",
                "09/04/2026"
        );

        assertTrue(duplicate);
    }

    @Test
    public void studentNewPassConflictingWithApprovedWindow_isBlocked() {
        VisitorRequest approvedExisting = request(
                "req-approved",
                "3520212345678",
                "09/04/2026",
                "11:00",
                "13:00",
                RequestStatus.APPROVED,
                false
        );

        List<VisitorRequest> existing = Collections.singletonList(approvedExisting);
        boolean clash = RequestValidationUtils.hasTimeClashWithApproved(existing, "09/04/2026", "12:00", "14:00");

        assertTrue(clash);
    }

    @Test
    public void studentPendingPass_blocksAnotherSubmission() {
        VisitorRequest pendingExisting = request(
                "req-pending",
                "3520212345679",
                "09/04/2026",
                "12:00",
                "13:00",
                RequestStatus.PENDING,
                false
        );

        assertTrue(RequestValidationUtils.hasBlockingStudentPass(Arrays.asList(pendingExisting)));
    }

    private static VisitorRequest request(
            String id,
            String cnic,
            String visitDate,
            String start,
            String end,
            String status,
            boolean onCampus
    ) {
        return new VisitorRequest(
                id,
                "Guest",
                cnic,
                "Meeting",
                visitDate,
                start,
                end,
                "FAC-1",
                "creator-1",
                "faculty",
                status,
                null,
                null,
                onCampus
        );
    }
}
