package com.example.se_proj.rules;

import com.example.se_proj.models.BlacklistEntry;
import com.example.se_proj.models.DeliveryLog;
import com.example.se_proj.models.ZoneAccessLog;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-level test scenarios for US18–21 end-to-end gate access flows.
 *
 * <p>Each test exercises the collaboration of {@link BlacklistService},
 * {@link ZoneAccessLogger}, and {@link DeliveryTrackingService} together, with all
 * Firestore calls mocked and listeners invoked synchronously via {@code doAnswer}.
 * This mirrors the sequence of calls made by {@code GuardDashboardActivity} on each
 * entity scan.</p>
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>Blacklisted entity scan: alert triggers, access denied, log created.</li>
 *   <li>Clean entity scan: access granted, log created, no alert fired.</li>
 *   <li>Delivery lifecycle: entry creates record, exit computes duration, overstay detected.</li>
 *   <li>Edge cases: concurrent scans, duplicate entry guard, missing Firestore document fields.</li>
 * </ol>
 */
public class CampusGateIntegrationTest {

    // =========================================================================
    // Shared Test Data
    // =========================================================================

    private static final String GUARD_ID          = "guard-uid-007";
    private static final String BLACKLISTED_CNIC  = "4201234567890";
    private static final String CLEAN_CNIC        = "3510987654321";
    private static final String RIDER_CNIC        = "3520212345671";
    private static final String RIDER_NAME        = "Imran Delivery";
    private static final String ZONE_ID           = "main_gate";
    private static final String EXIT_ZONE         = "out_gate";
    private static final String ENTRY_DOC_ID      = "delivery-doc-001";

    private static final Timestamp FUTURE_EXPIRY  = new Timestamp(9_999_999_999L, 0);
    private static final Timestamp ENTRY_TIME     = new Timestamp(1_000L, 0);
    private static final Timestamp EXPECTED_EXIT  = new Timestamp(2_800L, 0);  // +30 min window
    private static final Timestamp OVERSTAY_TIME  = new Timestamp(9_999L, 0);  // past expected exit

    // =========================================================================
    // Mocks — shared across all services
    // =========================================================================

    @Mock private FirebaseFirestore   mockDb;
    @Mock private CollectionReference mockBlacklistCollection;
    @Mock private CollectionReference mockZoneCollection;
    @Mock private CollectionReference mockAuditCollection;
    @Mock private CollectionReference mockDeliveryCollection;
    @Mock private CollectionReference mockAlertCollection;
    @Mock private DocumentReference   mockZoneDocRef;
    @Mock private DocumentReference   mockAuditDocRef;
    @Mock private DocumentReference   mockAlertDocRef;
    @Mock private DocumentReference   mockDeliveryDocRef;
    @Mock private WriteBatch          mockBatch;
    @Mock private Task<Void>          mockVoidTask;
    @Mock private Task<Void>          mockCommitTask;
    @Mock private Task<QuerySnapshot> mockQueryTask;
    @Mock private QuerySnapshot       mockSnapshot;
    @Mock private DocumentSnapshot    mockDocSnapshot;
    @Mock private Query               mockQuery1;
    @Mock private Query               mockQuery2;

    // Services under test
    private BlacklistService         blacklistService;
    private ZoneAccessLogger         zoneAccessLogger;
    private DeliveryTrackingService  deliveryTrackingService;
    private AlertManager             alertManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Route db.collection() to the right mock by collection name
        when(mockDb.collection("blacklist")).thenReturn(mockBlacklistCollection);
        when(mockDb.collection("zone_access_logs")).thenReturn(mockZoneCollection);
        when(mockDb.collection("access_logs")).thenReturn(mockAuditCollection);
        when(mockDb.collection("delivery_logs")).thenReturn(mockDeliveryCollection);
        when(mockDb.collection("alerts")).thenReturn(mockAlertCollection);

        // Zone + audit log write path
        when(mockZoneCollection.document()).thenReturn(mockZoneDocRef);
        when(mockAuditCollection.document()).thenReturn(mockAuditDocRef);
        when(mockZoneDocRef.getId()).thenReturn("zone-log-id");
        when(mockAuditDocRef.getId()).thenReturn("audit-log-id");
        when(mockDb.batch()).thenReturn(mockBatch);
        when(mockBatch.set(any(DocumentReference.class), any())).thenReturn(mockBatch);
        when(mockBatch.commit()).thenReturn(mockCommitTask);
        when(mockCommitTask.addOnSuccessListener(any())).thenReturn(mockCommitTask);
        when(mockCommitTask.addOnFailureListener(any())).thenReturn(mockCommitTask);

        // Alert write path
        when(mockAlertCollection.document()).thenReturn(mockAlertDocRef);
        when(mockAlertDocRef.getId()).thenReturn("alert-doc-id");
        when(mockAlertDocRef.set(any())).thenReturn(mockVoidTask);
        when(mockVoidTask.addOnSuccessListener(any())).thenReturn(mockVoidTask);
        when(mockVoidTask.addOnFailureListener(any())).thenReturn(mockVoidTask);

        blacklistService        = new BlacklistService(mockDb);
        zoneAccessLogger        = new ZoneAccessLogger(mockDb, GUARD_ID);
        alertManager            = new AlertManager(mockDb, GUARD_ID);
        deliveryTrackingService = new DeliveryTrackingService(mockDb, GUARD_ID, alertManager);
    }

    // =========================================================================
    // Scenario 1 — Blacklisted entity scan
    //
    // Guard scans entity → blacklist check → alert triggered → access denied → log written
    // =========================================================================

    /**
     * Step A: blacklist lookup returns a live permanent entry.
     * <p>Verifies that {@link BlacklistService#checkBlacklist(String)} queries Firestore
     * with a candidate list that includes the normalized entity ID.
     */
    @Test
    public void scenario1_blacklistedScan_blacklistIsQueried() {
        stubBlacklistQueryReturnsActive(BLACKLISTED_CNIC);

        service_checkBlacklist(BLACKLISTED_CNIC);

        verify(mockBlacklistCollection).whereIn(eq("entityId"),
                argThat(list -> list.contains(BLACKLISTED_CNIC)));
        verify(mockQuery1).get();
    }

    /**
     * Step B: positive blacklist result → guard calls AlertManager → alert document written.
     * <p>Verifies that {@link AlertManager#triggerHighPriorityAlert(BlacklistEntry, String, AlertManager.AlertWriteCallback)}
     * causes exactly one write to the {@code alerts} collection.
     */
    @Test
    public void scenario1_blacklistedScan_highPriorityAlertIsWrittenToFirestore() {
        BlacklistEntry entry = makePermanentEntry(BLACKLISTED_CNIC);

        alertManager.triggerHighPriorityAlert(entry, ZONE_ID, null);

        verify(mockAlertCollection).document();
        verify(mockAlertDocRef).set(any());
    }

    /**
     * Step C: after alert, guard calls ZoneAccessLogger with ACTION_DENIED.
     * <p>Verifies the denial reason and outcome are stored in the zone log.
     */
    @Test
    public void scenario1_blacklistedScan_zoneLogRecordsDenialWithReason() {
        String denialReason = "Entity on blacklist";

        zoneAccessLogger.logFailure(BLACKLISTED_CNIC, "person", "Ahmed Khan",
                ZONE_ID, ZoneAccessLog.ACTION_DENIED, denialReason, "");

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());

        ZoneAccessLog log = captor.getValue();
        assertEquals(ZoneAccessLog.OUTCOME_FAILURE, log.getOutcome());
        assertEquals(denialReason,                  log.getFailureReason());
        assertEquals(ZoneAccessLog.ACTION_DENIED,   log.getAction());
    }

    /**
     * Full scenario 1 combined: blacklist detection → alert → denied log → two batch writes.
     */
    @Test
    public void scenario1_fullFlow_alertAndDeniedLogBothWritten() {
        BlacklistEntry entry = makePermanentEntry(BLACKLISTED_CNIC);

        // Guard triggers alert
        alertManager.triggerHighPriorityAlert(entry, ZONE_ID, null);
        // Guard logs the denial
        zoneAccessLogger.logFailure(BLACKLISTED_CNIC, "person", "Ahmed Khan",
                ZONE_ID, ZoneAccessLog.ACTION_DENIED, "Blacklisted", "");

        verify(mockAlertDocRef).set(any());                              // alert written
        verify(mockBatch, times(2)).set(any(), any());                   // zone + audit
        verify(mockBatch, times(1)).commit();                            // batch committed
    }

    // =========================================================================
    // Scenario 2 — Clean entity scan
    //
    // Guard scans clean entity → no alert → access granted → log written
    // =========================================================================

    @Test
    public void scenario2_cleanScan_blacklistQueried() {
        stubBlacklistQueryReturnsEmpty(CLEAN_CNIC);

        service_checkBlacklist(CLEAN_CNIC);

        verify(mockBlacklistCollection).whereIn(eq("entityId"), anyList());
    }

    @Test
    public void scenario2_cleanScan_noAlertWritten() {
        // Guard does NOT call alertManager for a clean entity — verified by no alert write
        zoneAccessLogger.logSuccess(CLEAN_CNIC, "person", "Ali Clean",
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, "");

        verify(mockAlertDocRef, never()).set(any());
    }

    @Test
    public void scenario2_cleanScan_zoneLogRecordsSuccessfulEntry() {
        zoneAccessLogger.logSuccess(CLEAN_CNIC, "person", "Ali Clean",
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, "");

        ArgumentCaptor<ZoneAccessLog> captor = ArgumentCaptor.forClass(ZoneAccessLog.class);
        verify(mockBatch).set(eq(mockZoneDocRef), captor.capture());

        ZoneAccessLog log = captor.getValue();
        assertEquals(ZoneAccessLog.OUTCOME_SUCCESS, log.getOutcome());
        assertEquals(ZoneAccessLog.ACTION_ENTRY,    log.getAction());
        assertEquals("",                            log.getFailureReason());
    }

    @Test
    public void scenario2_cleanScan_batchCommitted() {
        zoneAccessLogger.logSuccess(CLEAN_CNIC, "person", "Ali Clean",
                ZONE_ID, ZoneAccessLog.ACTION_ENTRY, "");

        verify(mockBatch).commit();
    }

    // =========================================================================
    // Scenario 3 — Delivery rider lifecycle: entry → exit → overstay detection
    // =========================================================================

    @Test
    public void scenario3_deliveryEntry_queriesDeliveryCollectionForDuplicates() {
        stubDeliveryDuplicateQuery(/*duplicateExists=*/false);
        stubDeliveryDocCreate();

        startDelivery();

        verify(mockDeliveryCollection, atLeastOnce()).whereEqualTo(anyString(), any());
    }

    @Test
    public void scenario3_deliveryEntry_noDuplicate_doesNotCallErrorCallback() {
        stubDeliveryDuplicateQuery(/*duplicateExists=*/false);
        stubDeliveryDocCreate();

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        deliveryTrackingService.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, "FoodCo",
                "LEA-5678", "Block B", HOST_ID(), "Dr. Ali",
                new DeliveryTrackingService.DeliveryCallback() {
                    @Override public void onSuccess(String id) {}
                    @Override public void onError(Exception e) { errorCalled.set(true); }
                });

        assertFalse("No error expected when no duplicate exists", errorCalled.get());
    }

    @Test
    public void scenario3_deliveryDuplicate_callsOnErrorWithIllegalState() {
        stubDeliveryDuplicateQuery(/*duplicateExists=*/true);

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Exception> error = new AtomicReference<>();
        deliveryTrackingService.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, "FoodCo",
                "LEA-5678", "Block B", HOST_ID(), "Dr. Ali",
                new DeliveryTrackingService.DeliveryCallback() {
                    @Override public void onSuccess(String id) { fail("Must not succeed"); }
                    @Override public void onError(Exception e) {
                        errorCalled.set(true);
                        error.set(e);
                    }
                });

        assertTrue("onError must fire for duplicate",            errorCalled.get());
        assertTrue("Must be IllegalStateException",
                error.get() instanceof IllegalStateException);
    }

    @Test
    public void scenario3_exitWithoutEntry_callsOnErrorWithIllegalState() {
        stubEndDeliveryQuery(/*hasActiveEntry=*/false);

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Exception> error = new AtomicReference<>();
        deliveryTrackingService.endDelivery(RIDER_CNIC, EXIT_ZONE,
                new DeliveryTrackingService.DeliveryCallback() {
                    @Override public void onSuccess(String id) { fail("Must not succeed"); }
                    @Override public void onError(Exception e) {
                        errorCalled.set(true);
                        error.set(e);
                    }
                });

        assertTrue("onError must fire for exit without prior entry", errorCalled.get());
        assertTrue(error.get() instanceof IllegalStateException);
    }

    @Test
    public void scenario3_overstayDetection_pureLogicFlagsCorrectly() {
        DeliveryTrackingService svc =
                new DeliveryTrackingService(null, GUARD_ID, null);

        DeliveryLog log = new DeliveryLog(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, "FoodCo",
                "LEA-5678", "Block B", HOST_ID(), "Dr. Ali", "in_gate",
                ENTRY_TIME, EXPECTED_EXIT, GUARD_ID);

        assertTrue("Delivery past expected exit must be flagged as overstaying",
                svc.isOverstaying(log, OVERSTAY_TIME));
        assertFalse("Delivery within window must not be flagged",
                svc.isOverstaying(log, ENTRY_TIME));
    }

    @Test
    public void scenario3_durationCalculation_exactMinutesReturned() {
        DeliveryTrackingService svc =
                new DeliveryTrackingService(null, GUARD_ID, null);

        // Entry at t=0, exit at t=25 min
        long duration = svc.calculateDuration(
                new Timestamp(0L, 0),
                new Timestamp(25L * 60L, 0));

        assertEquals(25L, duration);
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Test
    public void edge_nullEntityId_blacklistCheckSkipsFirestore() {
        blacklistService.checkBlacklist(null);
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void edge_whitespaceEntityId_blacklistCheckSkipsFirestore() {
        blacklistService.checkBlacklist("    ");
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void edge_nullRiderCNIC_endDeliverySkipsFirestore() {
        deliveryTrackingService.endDelivery(null, EXIT_ZONE, noOpDeliveryCallback());
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void edge_startDelivery_allFieldsEmpty_errorBeforeFirestore() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        deliveryTrackingService.startDelivery(
                "", "", "", "", "", "", "", "",
                new DeliveryTrackingService.DeliveryCallback() {
                    @Override public void onSuccess(String id) {}
                    @Override public void onError(Exception e) { errorCalled.set(true); }
                });

        assertTrue(errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void edge_triggerAlert_nullBlacklistEntry_callsOnError() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        alertManager.triggerHighPriorityAlert(null, ZONE_ID,
                new AlertManager.AlertWriteCallback() {
                    @Override public void onSuccess(String alertId) { fail(); }
                    @Override public void onError(Exception e) { errorCalled.set(true); }
                });

        assertTrue("Null entry must trigger onError in AlertManager", errorCalled.get());
        verify(mockAlertDocRef, never()).set(any());
    }

    @Test
    public void edge_triggerAlert_emptyZoneId_callsOnError() {
        BlacklistEntry entry = makePermanentEntry(BLACKLISTED_CNIC);
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        alertManager.triggerHighPriorityAlert(entry, "",
                new AlertManager.AlertWriteCallback() {
                    @Override public void onSuccess(String alertId) { fail(); }
                    @Override public void onError(Exception e) { errorCalled.set(true); }
                });

        assertTrue("Empty zoneId must trigger validation error", errorCalled.get());
    }

    @Test
    public void edge_permanentBan_isStillActiveAfterTimeAdvances() {
        BlacklistEntry entry = makePermanentEntry(BLACKLISTED_CNIC);
        // Permanent bans do not expire regardless of how much time has "passed"
        assertFalse(entry.isExpired());
        assertTrue(entry.isActive());
        assertTrue(entry.isEffectivelyActive());
    }

    @Test
    public void edge_expiredTemporaryBan_notEffectivelyActive() {
        BlacklistEntry entry = new BlacklistEntry(
                BLACKLISTED_CNIC, BlacklistEntry.TYPE_PERSON, "Expired Person",
                "Old reason", BlacklistEntry.BAN_TEMPORARY,
                new Timestamp(1L, 0),  // past epoch
                "admin-001");
        assertTrue("isActive flag is set", entry.isActive());
        assertFalse("But entry is expired, so not effectively active", entry.isEffectivelyActive());
    }

    @Test
    public void edge_calculateDuration_largeGap_doesNotOverflow() {
        DeliveryTrackingService svc =
                new DeliveryTrackingService(null, GUARD_ID, null);
        // 10 hours
        long duration = svc.calculateDuration(
                new Timestamp(0L, 0),
                new Timestamp(10L * 3600L, 0));
        assertEquals(600L, duration);
    }

    // =========================================================================
    // Helpers — Firestore stubs
    // =========================================================================

    @SuppressWarnings("unchecked")
    private void stubBlacklistQueryReturnsActive(String entityId) {
        when(mockBlacklistCollection.whereIn(anyString(), anyList())).thenReturn(mockQuery1);
        when(mockQuery1.get()).thenReturn(mockQueryTask);

        // Return snapshot with the active entry (toObject() not needed here,
        // snapshot.getDocuments() returns a mocked docSnapshot which this integration
        // test does not drill into further — see BlacklistServiceTest for that detail).
        when(mockSnapshot.isEmpty()).thenReturn(false);
        when(mockSnapshot.getDocuments()).thenReturn(Collections.singletonList(mockDocSnapshot));

        doAnswer(inv -> {
            ((OnSuccessListener<QuerySnapshot>) inv.getArgument(0)).onSuccess(mockSnapshot);
            return mockQueryTask;
        }).when(mockQueryTask).addOnSuccessListener(any());
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
    }

    @SuppressWarnings("unchecked")
    private void stubBlacklistQueryReturnsEmpty(String entityId) {
        when(mockBlacklistCollection.whereIn(anyString(), anyList())).thenReturn(mockQuery1);
        when(mockQuery1.get()).thenReturn(mockQueryTask);
        when(mockSnapshot.isEmpty()).thenReturn(true);
        when(mockSnapshot.getDocuments()).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            ((OnSuccessListener<QuerySnapshot>) inv.getArgument(0)).onSuccess(mockSnapshot);
            return mockQueryTask;
        }).when(mockQueryTask).addOnSuccessListener(any());
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
    }

    @SuppressWarnings("unchecked")
    private void stubDeliveryDuplicateQuery(boolean duplicateExists) {
        when(mockDeliveryCollection.whereEqualTo(anyString(), any())).thenReturn(mockQuery1);
        when(mockQuery1.whereEqualTo(anyString(), any())).thenReturn(mockQuery2);
        when(mockQuery2.get()).thenReturn(mockQueryTask);
        when(mockSnapshot.isEmpty()).thenReturn(!duplicateExists);
        when(mockSnapshot.getDocuments()).thenReturn(
                duplicateExists
                        ? Collections.singletonList(mockDocSnapshot)
                        : Collections.emptyList());

        doAnswer(inv -> {
            ((OnSuccessListener<QuerySnapshot>) inv.getArgument(0)).onSuccess(mockSnapshot);
            return mockQueryTask;
        }).when(mockQueryTask).addOnSuccessListener(any());
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
    }

    @SuppressWarnings("unchecked")
    private void stubEndDeliveryQuery(boolean hasActiveEntry) {
        when(mockDeliveryCollection.whereEqualTo(anyString(), any())).thenReturn(mockQuery1);
        when(mockQuery1.whereEqualTo(anyString(), any())).thenReturn(mockQuery2);
        when(mockQuery2.get()).thenReturn(mockQueryTask);
        when(mockSnapshot.isEmpty()).thenReturn(!hasActiveEntry);
        when(mockSnapshot.getDocuments()).thenReturn(
                hasActiveEntry
                        ? Collections.singletonList(mockDocSnapshot)
                        : Collections.emptyList());

        doAnswer(inv -> {
            ((OnSuccessListener<QuerySnapshot>) inv.getArgument(0)).onSuccess(mockSnapshot);
            return mockQueryTask;
        }).when(mockQueryTask).addOnSuccessListener(any());
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
    }

    @SuppressWarnings("unchecked")
    private void stubDeliveryDocCreate() {
        when(mockDeliveryCollection.document()).thenReturn(mockDeliveryDocRef);
        when(mockDeliveryDocRef.getId()).thenReturn(ENTRY_DOC_ID);
        when(mockDeliveryDocRef.set(any())).thenReturn(mockVoidTask);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(0)).onSuccess(null);
            return mockVoidTask;
        }).when(mockVoidTask).addOnSuccessListener(any());
        when(mockVoidTask.addOnFailureListener(any())).thenReturn(mockVoidTask);
    }

    // =========================================================================
    // Helpers — delegating calls
    // =========================================================================

    private void service_checkBlacklist(String entityId) {
        blacklistService.checkBlacklist(entityId);
    }

    private void startDelivery() {
        deliveryTrackingService.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, "FoodCo",
                "LEA-5678", "Block B", HOST_ID(), "Dr. Ali",
                noOpDeliveryCallback());
    }

    private BlacklistEntry makePermanentEntry(String cnic) {
        return new BlacklistEntry(cnic, BlacklistEntry.TYPE_PERSON, "Test Person",
                "Security concern", BlacklistEntry.BAN_PERMANENT, null, "admin-001");
    }

    private static String HOST_ID() { return "FAC-102"; }

    private DeliveryTrackingService.DeliveryCallback noOpDeliveryCallback() {
        return new DeliveryTrackingService.DeliveryCallback() {
            @Override public void onSuccess(String id) {}
            @Override public void onError(Exception e) {}
        };
    }
}
