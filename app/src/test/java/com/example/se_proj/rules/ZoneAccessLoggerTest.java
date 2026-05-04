package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.models.ZoneAccessLog;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZoneAccessLogger} (US18).
 *
 * <p>Tests are split into two concerns:
 * <ol>
 *   <li><b>ZoneAccessLog model</b> — pure field construction and normalization verified
 *       without touching Firestore.</li>
 *   <li><b>Logger Firestore interactions</b> — verifies that {@code logAccess} and its
 *       convenience overloads write exactly two documents per call (one zone log +
 *       one audit mirror) using a WriteBatch.</li>
 * </ol>
 */
public class ZoneAccessLoggerTest {

    // =========================================================================
    // Test Data
    // =========================================================================

    private static final String GUARD_ID     = "guard-uid-007";
    private static final String ENTITY_ID    = "4201234567890";
    private static final String ENTITY_NAME  = "Ali Raza";
    private static final String ZONE_ID      = "main_gate";
    private static final String REQUEST_ID   = "req-doc-xyz";
    private static final String ZONE_DOC_ID  = "zone-log-generated-id";
    private static final String AUDIT_DOC_ID = "audit-log-generated-id";

    // =========================================================================
    // Mocks
    // =========================================================================

    @Mock private FirebaseFirestore    mockDb;
    @Mock private CollectionReference  mockZoneCollection;
    @Mock private CollectionReference  mockAuditCollection;
    @Mock private DocumentReference    mockZoneDocRef;
    @Mock private DocumentReference    mockAuditDocRef;
    @Mock private WriteBatch           mockBatch;
    @Mock private Task<Void>           mockCommitTask;

    private ZoneAccessLogger logger;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockDb.collection("zone_access_logs")).thenReturn(mockZoneCollection);
        when(mockDb.collection("access_logs")).thenReturn(mockAuditCollection);
        when(mockZoneCollection.document()).thenReturn(mockZoneDocRef);
        when(mockAuditCollection.document()).thenReturn(mockAuditDocRef);
        when(mockZoneDocRef.getId()).thenReturn(ZONE_DOC_ID);
        when(mockAuditDocRef.getId()).thenReturn(AUDIT_DOC_ID);
        when(mockDb.batch()).thenReturn(mockBatch);
        when(mockBatch.set(any(DocumentReference.class), any())).thenReturn(mockBatch);
        when(mockBatch.commit()).thenReturn(mockCommitTask);
        when(mockCommitTask.addOnSuccessListener(any())).thenReturn(mockCommitTask);
        when(mockCommitTask.addOnFailureListener(any())).thenReturn(mockCommitTask);

        logger = new ZoneAccessLogger(mockDb, GUARD_ID);
    }

    // =========================================================================
    // 1. ZoneAccessLog Model Tests — pure construction, no Firestore
    // =========================================================================

    @Test
    public void zoneAccessLog_personType_storesCorrectFields() {
        ZoneAccessLog log = new ZoneAccessLog(
                ENTITY_ID, "person", ENTITY_NAME,
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS,
                "", GUARD_ID, REQUEST_ID);

        assertEquals(ENTITY_ID,   log.getEntityId());
        assertEquals("person",    log.getEntityType());
        assertEquals(ENTITY_NAME, log.getEntityName());
        assertEquals(ZONE_ID,     log.getZoneId());
        assertEquals(ZoneAccessLog.ACTION_ENTRY,    log.getAction());
        assertEquals(ZoneAccessLog.OUTCOME_SUCCESS, log.getOutcome());
        assertEquals(GUARD_ID,    log.getGuardId());
        assertEquals(REQUEST_ID,  log.getRequestId());
        assertNotNull("Timestamp must be set at construction", log.getTimestamp());
    }

    @Test
    public void zoneAccessLog_vehicleType_storesCorrectEntityType() {
        ZoneAccessLog log = new ZoneAccessLog(
                "LEA1234", "vehicle", "Guard Van",
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS,
                "", GUARD_ID, "");

        assertEquals("vehicle", log.getEntityType());
    }

    @Test
    public void zoneAccessLog_successOutcome_resultIsALLOWED() {
        ZoneAccessLog log = new ZoneAccessLog(
                ENTITY_ID, "person", ENTITY_NAME,
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS,
                "", GUARD_ID, "");

        assertEquals("ALLOWED", log.getResult());
    }

    @Test
    public void zoneAccessLog_failureOutcome_resultIsDENIED() {
        ZoneAccessLog log = new ZoneAccessLog(
                ENTITY_ID, "person", ENTITY_NAME,
                ZONE_ID, ZoneAccessLog.ACTION_DENIED, ZoneAccessLog.OUTCOME_FAILURE,
                "Blacklisted", GUARD_ID, "");

        assertEquals("DENIED",     log.getResult());
        assertEquals("Blacklisted", log.getFailureReason());
    }

    @Test
    public void zoneAccessLog_nullEntityId_defaultsToEmptyString() {
        ZoneAccessLog log = new ZoneAccessLog(
                null, "person", ENTITY_NAME,
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS,
                "", GUARD_ID, "");

        assertEquals("", log.getEntityId());
    }

    @Test
    public void zoneAccessLog_studentRollNoMirrorsEntityId() {
        ZoneAccessLog log = new ZoneAccessLog(
                ENTITY_ID, "person", ENTITY_NAME,
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS,
                "", GUARD_ID, REQUEST_ID);

        assertEquals(ENTITY_ID, log.getStudentRollNo());
    }

    @Test
    public void zoneAccessLog_wingMirrorsZoneId() {
        ZoneAccessLog log = new ZoneAccessLog(
                ENTITY_ID, "person", ENTITY_NAME,
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS,
                "", GUARD_ID, REQUEST_ID);

        assertEquals(ZONE_ID, log.getWing());
    }

    @Test
    public void zoneAccessLog_setLogId_isReflected() {
        ZoneAccessLog log = new ZoneAccessLog();
        log.setLogId("new-id");
        assertEquals("new-id", log.getLogId());
    }

    // =========================================================================
    // 2. logSuccess — Firestore interaction
    // =========================================================================

    @Test
    public void logSuccess_commitsBatchWithTwoSetCalls() {
        logger.logSuccess(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, REQUEST_ID);

        verify(mockBatch, times(2)).set(any(DocumentReference.class), any());
        verify(mockBatch, times(1)).commit();
    }

    @Test
    public void logSuccess_writesZoneLogToCorrectCollection() {
        logger.logSuccess(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, REQUEST_ID);

        // zone_access_logs collection must produce a new document reference
        verify(mockZoneCollection, times(1)).document();
        verify(mockBatch).set(eq(mockZoneDocRef), any());
    }

    @Test
    public void logSuccess_writesAuditMirrorToCorrectCollection() {
        logger.logSuccess(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, REQUEST_ID);

        verify(mockAuditCollection, times(1)).document();
        verify(mockBatch).set(eq(mockAuditDocRef), any());
    }

    @Test
    public void logSuccess_storedZoneLogHasSuccessOutcome() {
        logger.logSuccess(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, REQUEST_ID);

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());

        ZoneAccessLog stored = captor.getValue();
        assertEquals(ZoneAccessLog.OUTCOME_SUCCESS, stored.getOutcome());
        assertEquals("", stored.getFailureReason());
    }

    @Test
    public void logSuccess_storedZoneLogHasCorrectEntityAndZone() {
        logger.logSuccess(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, REQUEST_ID);

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());

        ZoneAccessLog stored = captor.getValue();
        assertEquals(ENTITY_ID,   stored.getEntityId());
        assertEquals(ENTITY_NAME, stored.getEntityName());
        assertEquals(ZONE_ID,     stored.getZoneId());
        assertEquals(GUARD_ID,    stored.getGuardId());
    }

    // =========================================================================
    // 3. logFailure — Firestore interaction
    // =========================================================================

    @Test
    public void logFailure_commitsBatchWithTwoSetCalls() {
        logger.logFailure(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_DENIED, "Entity blacklisted", REQUEST_ID);

        verify(mockBatch, times(2)).set(any(DocumentReference.class), any());
        verify(mockBatch, times(1)).commit();
    }

    @Test
    public void logFailure_storedZoneLogHasFailureOutcomeAndReason() {
        String denialReason = "Entity blacklisted";

        logger.logFailure(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_DENIED, denialReason, REQUEST_ID);

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());

        ZoneAccessLog stored = captor.getValue();
        assertEquals(ZoneAccessLog.OUTCOME_FAILURE, stored.getOutcome());
        assertEquals(denialReason,                  stored.getFailureReason());
        assertEquals(ZoneAccessLog.ACTION_DENIED,   stored.getAction());
    }

    // =========================================================================
    // 4. logAccess — Normalization edge cases
    // =========================================================================

    @Test
    public void logAccess_unknownEntityType_defaultsToPerson() {
        logger.logAccess(ENTITY_ID, "UNKNOWN_TYPE", ENTITY_NAME, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS, "", REQUEST_ID);

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());
        assertEquals("person", captor.getValue().getEntityType());
    }

    @Test
    public void logAccess_emptyZoneId_fallsBackToUnknownZone() {
        logger.logAccess(ENTITY_ID, "person", ENTITY_NAME, "",
                ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS, "", REQUEST_ID);

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());
        assertEquals("unknown_zone", captor.getValue().getZoneId());
    }

    @Test
    public void logAccess_nullZoneId_fallsBackToUnknownZone() {
        logger.logAccess(ENTITY_ID, "person", ENTITY_NAME, null,
                ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS, "", REQUEST_ID);

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());
        assertEquals("unknown_zone", captor.getValue().getZoneId());
    }

    @Test
    public void logAccess_vehicleEntityType_preservedCorrectly() {
        logger.logAccess("ABC123", "vehicle", "Delivery Van", ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS, "", "");

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());
        assertEquals("vehicle", captor.getValue().getEntityType());
    }

    @Test
    public void logAccess_nullEntityIdAndName_doesNotThrow() {
        assertDoesNotThrow(() ->
                logger.logAccess(null, "person", null, ZONE_ID,
                        ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS, "", ""));
    }

    // =========================================================================
    // 5. logAccessForVisitor — convenience overload
    // =========================================================================

    @Test
    public void logAccessForVisitor_validRequest_callsCommit() {
        VisitorRequest request = makeVisitorRequest();

        logger.logAccessForVisitor(request, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS, "");

        verify(mockBatch, times(1)).commit();
    }

    @Test
    public void logAccessForVisitor_nullRequest_doesNotThrowAndStillCallsCommit() {
        assertDoesNotThrow(() ->
                logger.logAccessForVisitor(null, ZONE_ID,
                        ZoneAccessLog.ACTION_DENIED, ZoneAccessLog.OUTCOME_FAILURE, "No request"));

        verify(mockBatch, times(1)).commit();
    }

    @Test
    public void logAccessForVisitor_usesGuestCNICFromRequest() {
        VisitorRequest request = makeVisitorRequest();

        logger.logAccessForVisitor(request, ZONE_ID,
                ZoneAccessLog.ACTION_ENTRY, ZoneAccessLog.OUTCOME_SUCCESS, "");

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());
        assertEquals("3510123456789", captor.getValue().getEntityId());
    }

    // =========================================================================
    // 6. Commit failure — fire-and-forget: logger must not propagate exception
    // =========================================================================

    @Test
    public void logSuccess_commitFailure_doesNotThrow() {
        RuntimeException commitError = new RuntimeException("Firestore timeout");
        when(mockBatch.commit()).thenReturn(mockCommitTask);
        doAnswer(inv -> {
            ((OnFailureListener) inv.getArgument(0)).onFailure(commitError);
            return mockCommitTask;
        }).when(mockCommitTask).addOnFailureListener(any());
        when(mockCommitTask.addOnSuccessListener(any())).thenReturn(mockCommitTask);

        assertDoesNotThrow(() ->
                logger.logSuccess(ENTITY_ID, "person", ENTITY_NAME, ZONE_ID,
                        ZoneAccessLog.ACTION_ENTRY, REQUEST_ID));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            fail("Expected no exception but got: " + t);
        }
    }

    private VisitorRequest makeVisitorRequest() {
        VisitorRequest req = new VisitorRequest();
        req.setRequestId(REQUEST_ID);
        req.setGuestCNIC("3510123456789");
        req.setGuestName("Visitor One");
        req.setHostId("FAC-001");
        return req;
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
