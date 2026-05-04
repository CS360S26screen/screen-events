package com.example.se_proj.rules;

import com.example.se_proj.models.DeliveryLog;
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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DeliveryTrackingService} (US21).
 *
 * <h3>Test layers</h3>
 * <ol>
 *   <li><b>Pure computation</b> — {@link DeliveryTrackingService#calculateDuration} and
 *       {@link DeliveryTrackingService#isOverstaying} require no Firestore, run instantly.</li>
 *   <li><b>Validation</b> — empty/null inputs fail synchronously before any Firestore call;
 *       verified with {@code verify(mockDb, never())}.</li>
 *   <li><b>Firestore paths</b> — mocked with Mockito {@code doAnswer} to invoke
 *       listeners synchronously in the test thread.</li>
 * </ol>
 */
public class DeliveryTrackingServiceTest {

    // =========================================================================
    // Test Data
    // =========================================================================

    private static final String GUARD_ID          = "guard-uid-007";
    private static final String RIDER_CNIC        = "3520212345671";
    private static final String RIDER_NAME        = "Imran Delivery";
    private static final String COMPANY_NAME      = "FoodExpress";
    private static final String VEHICLE_NUMBER    = "LEA-1234";
    private static final String DESTINATION_BLOCK = "Faculty Block A";
    private static final String HOST_ID           = "FAC-102";
    private static final String HOST_NAME         = "Dr. Khan";
    private static final String ENTRY_ZONE        = "in_gate";
    private static final String EXIT_ZONE         = "out_gate";

    /** Entry timestamp at Unix second 1000. */
    private static final Timestamp ENTRY_TIME    = new Timestamp(1_000L, 0);
    /** Expected exit = entry + 30 min (1800 s). */
    private static final Timestamp EXPECTED_EXIT = new Timestamp(1_000L + 1_800L, 0);

    // =========================================================================
    // Mocks
    // =========================================================================

    @Mock private FirebaseFirestore   mockDb;
    @Mock private CollectionReference mockCollection;
    @Mock private Query               mockQuery1;
    @Mock private Query               mockQuery2;
    @Mock private Task<QuerySnapshot> mockQueryTask;
    @Mock private QuerySnapshot       mockSnapshot;
    @Mock private DocumentSnapshot    mockDocSnapshot;
    @Mock private DocumentReference   mockDocRef;
    @Mock private Task<Void>          mockVoidTask;

    private DeliveryTrackingService service;
    private AlertManager            mockAlertManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockAlertManager = mock(AlertManager.class);
        service = new DeliveryTrackingService(mockDb, GUARD_ID, mockAlertManager);
    }

    // =========================================================================
    // 1. calculateDuration — pure computation
    // =========================================================================

    @Test
    public void calculateDuration_42Minutes_returnsExact42() {
        long duration = service.calculateDuration(
                new Timestamp(1_000L, 0),
                new Timestamp(1_000L + 42L * 60L, 0));

        assertEquals(42L, duration);
    }

    @Test
    public void calculateDuration_exactlyWindowMinutes_returnsWindow() {
        long duration = service.calculateDuration(
                ENTRY_TIME,
                new Timestamp(ENTRY_TIME.getSeconds() + DeliveryTrackingService.DELIVERY_WINDOW_MINUTES * 60L, 0));

        assertEquals(DeliveryTrackingService.DELIVERY_WINDOW_MINUTES, duration);
    }

    @Test
    public void calculateDuration_nullEntryTime_returnsZero() {
        assertEquals(0L, service.calculateDuration(null, EXPECTED_EXIT));
    }

    @Test
    public void calculateDuration_nullExitTime_returnsZero() {
        assertEquals(0L, service.calculateDuration(ENTRY_TIME, null));
    }

    @Test
    public void calculateDuration_bothNull_returnsZero() {
        assertEquals(0L, service.calculateDuration(null, null));
    }

    @Test
    public void calculateDuration_exitBeforeEntry_returnsZeroNotNegative() {
        // exit is earlier than entry — must clamp to 0
        long duration = service.calculateDuration(EXPECTED_EXIT, ENTRY_TIME);
        assertEquals(0L, duration);
    }

    @Test
    public void calculateDuration_sameTimestamps_returnsZero() {
        assertEquals(0L, service.calculateDuration(ENTRY_TIME, ENTRY_TIME));
    }

    // =========================================================================
    // 2. isOverstaying — pure computation
    // =========================================================================

    @Test
    public void isOverstaying_activeDeliveryPastExpectedExit_returnsTrue() {
        DeliveryLog log = makeActiveDelivery();
        // now = 2 seconds after expectedExitTime
        Timestamp now = new Timestamp(EXPECTED_EXIT.getSeconds() + 2L, 0);
        assertTrue(service.isOverstaying(log, now));
    }

    @Test
    public void isOverstaying_activeDeliveryBeforeExpectedExit_returnsFalse() {
        DeliveryLog log = makeActiveDelivery();
        // now = halfway through the allowed window
        Timestamp now = new Timestamp(ENTRY_TIME.getSeconds() + 500L, 0);
        assertFalse(service.isOverstaying(log, now));
    }

    @Test
    public void isOverstaying_completedDelivery_returnsFalse() {
        DeliveryLog log = makeActiveDelivery();
        log.setStatus(DeliveryLog.STATUS_COMPLETED);
        Timestamp pastExpiry = new Timestamp(EXPECTED_EXIT.getSeconds() + 9_999L, 0);
        assertFalse("Completed delivery must never be flagged as overstaying",
                service.isOverstaying(log, pastExpiry));
    }

    @Test
    public void isOverstaying_overstayStatusDelivery_returnsFalse() {
        DeliveryLog log = makeActiveDelivery();
        log.setStatus(DeliveryLog.STATUS_OVERSTAY);
        Timestamp pastExpiry = new Timestamp(EXPECTED_EXIT.getSeconds() + 9_999L, 0);
        assertFalse("STATUS_OVERSTAY (closed) must not report as currently overstaying",
                service.isOverstaying(log, pastExpiry));
    }

    @Test
    public void isOverstaying_nullLog_returnsFalse() {
        assertFalse(service.isOverstaying(null, Timestamp.now()));
    }

    @Test
    public void isOverstaying_nullNowTimestamp_returnsFalse() {
        assertFalse(service.isOverstaying(makeActiveDelivery(), null));
    }

    @Test
    public void isOverstaying_nullExpectedExitTime_returnsFalse() {
        DeliveryLog log = makeActiveDelivery();
        log.setExpectedExitTime(null);
        assertFalse("Null expectedExitTime must not be treated as expired",
                service.isOverstaying(log, new Timestamp(EXPECTED_EXIT.getSeconds() + 1L, 0)));
    }

    @Test
    public void isOverstaying_atExactExpectedExitTime_returnsTrue() {
        DeliveryLog log = makeActiveDelivery();
        // compareTo == 0 is treated as overstay (>= 0)
        assertTrue(service.isOverstaying(log, EXPECTED_EXIT));
    }

    // =========================================================================
    // 3. startDelivery — validation (synchronous, no Firestore)
    // =========================================================================

    @Test
    public void startDelivery_emptyCNIC_callsOnErrorImmediately() {
        AtomicBoolean errorCalled  = new AtomicBoolean(false);
        AtomicReference<Exception> error = new AtomicReference<>();

        service.startDelivery(
                "", RIDER_NAME, /* empty cnic */ "", COMPANY_NAME,
                VEHICLE_NUMBER, DESTINATION_BLOCK, HOST_ID, HOST_NAME,
                new DeliveryCallback() {
                    @Override public void onSuccess(String id) { fail("Must not succeed"); }
                    @Override public void onError(Exception e) {
                        errorCalled.set(true);
                        error.set(e);
                    }
                });

        assertTrue("onError must be called synchronously",         errorCalled.get());
        assertTrue("Error must be IllegalArgumentException",
                error.get() instanceof IllegalArgumentException);
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void startDelivery_emptyRiderName_callsOnErrorBeforeFirestore() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        service.startDelivery(
                RIDER_CNIC, /* empty name */ "", RIDER_CNIC, COMPANY_NAME,
                VEHICLE_NUMBER, DESTINATION_BLOCK, HOST_ID, HOST_NAME,
                errorCapturingCallback(errorCalled));

        assertTrue(errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void startDelivery_emptyCompanyName_callsOnError() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        service.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, /* empty company */ "",
                VEHICLE_NUMBER, DESTINATION_BLOCK, HOST_ID, HOST_NAME,
                errorCapturingCallback(errorCalled));

        assertTrue(errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void startDelivery_emptyVehicleNumber_callsOnError() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        service.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, COMPANY_NAME,
                /* empty vehicle */ "", DESTINATION_BLOCK, HOST_ID, HOST_NAME,
                errorCapturingCallback(errorCalled));

        assertTrue(errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void startDelivery_emptyDestinationBlock_callsOnError() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        service.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, COMPANY_NAME,
                VEHICLE_NUMBER, /* empty destination */ "", HOST_ID, HOST_NAME,
                errorCapturingCallback(errorCalled));

        assertTrue(errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void startDelivery_emptyHostId_callsOnError() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        service.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, COMPANY_NAME,
                VEHICLE_NUMBER, DESTINATION_BLOCK, /* empty host id */ "", HOST_NAME,
                errorCapturingCallback(errorCalled));

        assertTrue(errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    // =========================================================================
    // 4. startDelivery — duplicate detection (mocked Firestore)
    // =========================================================================

    @Test
    public void startDelivery_activeEntryExistsForCNIC_callsOnErrorWithIllegalState() {
        stubDuplicateDeliveryQuery(/*duplicateExists=*/true);

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Exception> error = new AtomicReference<>();

        service.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, COMPANY_NAME,
                VEHICLE_NUMBER, DESTINATION_BLOCK, HOST_ID, HOST_NAME,
                new DeliveryCallback() {
                    @Override public void onSuccess(String id) { fail("Must not succeed"); }
                    @Override public void onError(Exception e) {
                        errorCalled.set(true);
                        error.set(e);
                    }
                });

        assertTrue("onError must be called when duplicate exists", errorCalled.get());
        assertTrue("Must be IllegalStateException for duplicate active delivery",
                error.get() instanceof IllegalStateException);
    }

    @Test
    public void startDelivery_noExistingEntry_queriesDeliveryLogsCollection() {
        stubDuplicateDeliveryQuery(/*duplicateExists=*/false);
        stubDeliveryDocumentCreate();

        service.startDelivery(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC, COMPANY_NAME,
                VEHICLE_NUMBER, DESTINATION_BLOCK, HOST_ID, HOST_NAME,
                noOpDeliveryCallback());

        verify(mockDb, atLeastOnce()).collection("delivery_logs");
    }

    // =========================================================================
    // 5. endDelivery — validation (synchronous, no Firestore)
    // =========================================================================

    @Test
    public void endDelivery_nullCNIC_callsOnErrorImmediately() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Exception> error = new AtomicReference<>();

        service.endDelivery(null, EXIT_ZONE, new DeliveryCallback() {
            @Override public void onSuccess(String id) { fail("Must not succeed"); }
            @Override public void onError(Exception e) {
                errorCalled.set(true);
                error.set(e);
            }
        });

        assertTrue("onError must be called synchronously for null CNIC", errorCalled.get());
        assertTrue(error.get() instanceof IllegalArgumentException);
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void endDelivery_emptyCNIC_callsOnErrorImmediately() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        service.endDelivery("", EXIT_ZONE, errorCapturingCallback(errorCalled));

        assertTrue("onError must be called for empty CNIC", errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void endDelivery_whitespaceCNIC_callsOnErrorImmediately() {
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        service.endDelivery("   ", EXIT_ZONE, errorCapturingCallback(errorCalled));

        assertTrue(errorCalled.get());
        verify(mockDb, never()).collection(anyString());
    }

    // =========================================================================
    // 6. endDelivery — exit without entry (mocked Firestore)
    // =========================================================================

    @Test
    public void endDelivery_noActiveDeliveryFound_callsOnErrorWithIllegalState() {
        stubEndDeliveryQuery(/*hasActiveEntry=*/false);

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<Exception> error = new AtomicReference<>();

        service.endDelivery(RIDER_CNIC, EXIT_ZONE, new DeliveryCallback() {
            @Override public void onSuccess(String id) { fail("Must not succeed"); }
            @Override public void onError(Exception e) {
                errorCalled.set(true);
                error.set(e);
            }
        });

        assertTrue("onError fire for exit without entry",     errorCalled.get());
        assertTrue("Must be IllegalStateException",
                error.get() instanceof IllegalStateException);
        assertTrue("Message must mention no active delivery",
                error.get().getMessage().contains("No active delivery"));
    }

    @Test
    public void endDelivery_noActiveEntry_queriesCorrectCollection() {
        stubEndDeliveryQuery(/*hasActiveEntry=*/false);

        service.endDelivery(RIDER_CNIC, EXIT_ZONE, noOpDeliveryCallback());

        verify(mockDb).collection("delivery_logs");
    }

    // =========================================================================
    // 7. DeliveryLog Model Tests
    // =========================================================================

    @Test
    public void deliveryLog_newEntry_defaultsToActiveStatus() {
        DeliveryLog log = makeActiveDelivery();
        assertEquals(DeliveryLog.STATUS_ACTIVE, log.getStatus());
    }

    @Test
    public void deliveryLog_newEntry_overstayFlaggedIsFalse() {
        assertFalse(makeActiveDelivery().isOverstayFlagged());
    }

    @Test
    public void deliveryLog_setStatus_changesStatus() {
        DeliveryLog log = makeActiveDelivery();
        log.setStatus(DeliveryLog.STATUS_COMPLETED);
        assertEquals(DeliveryLog.STATUS_COMPLETED, log.getStatus());
    }

    @Test
    public void deliveryLog_setOverstayFlagged_changesFlag() {
        DeliveryLog log = makeActiveDelivery();
        log.setOverstayFlagged(true);
        assertTrue(log.isOverstayFlagged());
    }

    @Test
    public void deliveryLog_setDurationMinutes_isReflected() {
        DeliveryLog log = makeActiveDelivery();
        log.setDurationMinutes(25L);
        assertEquals(25L, log.getDurationMinutes());
    }

    // =========================================================================
    // Helpers — Firestore stubs
    // =========================================================================

    /**
     * Stubs the duplicate-guard query in startDelivery.
     * Chain: db.collection → whereEqualTo(cnic) → whereEqualTo(status) → get
     */
    @SuppressWarnings("unchecked")
    private void stubDuplicateDeliveryQuery(boolean duplicateExists) {
        when(mockDb.collection("delivery_logs")).thenReturn(mockCollection);
        when(mockCollection.whereEqualTo(anyString(), any())).thenReturn(mockQuery1);
        when(mockQuery1.whereEqualTo(anyString(), any())).thenReturn(mockQuery2);
        when(mockQuery2.get()).thenReturn(mockQueryTask);

        when(mockSnapshot.isEmpty()).thenReturn(!duplicateExists);
        if (duplicateExists) {
            when(mockSnapshot.getDocuments()).thenReturn(Collections.singletonList(mockDocSnapshot));
        } else {
            when(mockSnapshot.getDocuments()).thenReturn(Collections.emptyList());
        }

        doAnswer(inv -> {
            ((OnSuccessListener<QuerySnapshot>) inv.getArgument(0)).onSuccess(mockSnapshot);
            return mockQueryTask;
        }).when(mockQueryTask).addOnSuccessListener(any());
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
    }

    /**
     * Stubs the active-entry lookup in endDelivery.
     */
    @SuppressWarnings("unchecked")
    private void stubEndDeliveryQuery(boolean hasActiveEntry) {
        when(mockDb.collection("delivery_logs")).thenReturn(mockCollection);
        when(mockCollection.whereEqualTo(anyString(), any())).thenReturn(mockQuery1);
        when(mockQuery1.whereEqualTo(anyString(), any())).thenReturn(mockQuery2);
        when(mockQuery2.get()).thenReturn(mockQueryTask);

        when(mockSnapshot.isEmpty()).thenReturn(!hasActiveEntry);
        if (hasActiveEntry) {
            when(mockSnapshot.getDocuments()).thenReturn(Collections.singletonList(mockDocSnapshot));
        } else {
            when(mockSnapshot.getDocuments()).thenReturn(Collections.emptyList());
        }

        doAnswer(inv -> {
            ((OnSuccessListener<QuerySnapshot>) inv.getArgument(0)).onSuccess(mockSnapshot);
            return mockQueryTask;
        }).when(mockQueryTask).addOnSuccessListener(any());
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
    }

    /** Stubs document creation so createDeliveryDocument does not NPE. */
    @SuppressWarnings("unchecked")
    private void stubDeliveryDocumentCreate() {
        when(mockCollection.document()).thenReturn(mockDocRef);
        when(mockDocRef.getId()).thenReturn("delivery-doc-id");
        when(mockDocRef.set(any())).thenReturn(mockVoidTask);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(0)).onSuccess(null);
            return mockVoidTask;
        }).when(mockVoidTask).addOnSuccessListener(any());
        when(mockVoidTask.addOnFailureListener(any())).thenReturn(mockVoidTask);
    }

    // =========================================================================
    // Helpers — factory methods
    // =========================================================================

    private DeliveryLog makeActiveDelivery() {
        return new DeliveryLog(
                RIDER_CNIC, RIDER_NAME, RIDER_CNIC,
                COMPANY_NAME, VEHICLE_NUMBER, DESTINATION_BLOCK,
                HOST_ID, HOST_NAME, ENTRY_ZONE,
                ENTRY_TIME, EXPECTED_EXIT, GUARD_ID);
    }

    private DeliveryCallback errorCapturingCallback(AtomicBoolean errorCalled) {
        return new DeliveryCallback() {
            @Override public void onSuccess(String id) {}
            @Override public void onError(Exception e) { errorCalled.set(true); }
        };
    }

    private DeliveryCallback noOpDeliveryCallback() {
        return new DeliveryCallback() {
            @Override public void onSuccess(String id) {}
            @Override public void onError(Exception e) {}
        };
    }

    /** Local alias so tests can reference the callback type without full package. */
    interface DeliveryCallback extends DeliveryTrackingService.DeliveryCallback {}
}
