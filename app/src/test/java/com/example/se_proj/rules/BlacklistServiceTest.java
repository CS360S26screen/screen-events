package com.example.se_proj.rules;

import com.example.se_proj.models.BlacklistEntry;
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

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BlacklistService} (US19–20).
 *
 * <p>Tests are divided into two layers:
 * <ol>
 *   <li><b>Model layer</b> — pure {@link BlacklistEntry} logic with no mocking.</li>
 *   <li><b>Service layer</b> — Firestore interactions mocked via Mockito with synchronous
 *       doAnswer stubs so listener callbacks fire in the test thread.</li>
 * </ol>
 */
public class BlacklistServiceTest {

    // =========================================================================
    // Test Data
    // =========================================================================

    private static final String PERSON_CNIC   = "4201234567890";
    private static final String VEHICLE_PLATE = "FT454";
    private static final String VEHICLE_RAW   = "FT-454";  // input with dash
    private static final String ENTITY_NAME   = "Ahmed Khan";
    private static final String REASON        = "Unauthorized entry attempt";
    private static final String ADMIN_UID     = "admin-uid-001";
    private static final String ENTRY_DOC_ID  = "blacklist-doc-abc123";

    /** Timestamp safely in the future so temporary bans are not expired. */
    private static final Timestamp FUTURE_EXPIRY = new Timestamp(9_999_999_999L, 0);
    /** Epoch + 1 second — guaranteed to be in the past. */
    private static final Timestamp PAST_EXPIRY   = new Timestamp(1L, 0);

    // =========================================================================
    // Mocks
    // =========================================================================

    @Mock private FirebaseFirestore         mockDb;
    @Mock private CollectionReference       mockCollection;
    @Mock private Query                     mockQuery;
    @Mock private Task<QuerySnapshot>       mockQueryTask;
    @Mock private QuerySnapshot             mockSnapshot;
    @Mock private DocumentSnapshot          mockDocSnapshot;
    @Mock private DocumentReference         mockDocRef;
    @Mock private Task<Void>                mockVoidTask;
    @Mock private Task<DocumentReference>   mockAddTask;

    private BlacklistService service;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new BlacklistService(mockDb);
    }

    // =========================================================================
    // 1. BlacklistEntry Model Tests — pure logic, no Firestore
    // =========================================================================

    @Test
    public void permanentBan_isNeverExpired() {
        BlacklistEntry entry = makePermanentEntry();
        assertFalse("Permanent bans must never expire regardless of time", entry.isExpired());
    }

    @Test
    public void temporaryBan_futureExpiry_isNotExpiredYet() {
        BlacklistEntry entry = makeTemporaryEntry(FUTURE_EXPIRY);
        assertFalse("Ban with future expiry must not be expired", entry.isExpired());
    }

    @Test
    public void temporaryBan_pastExpiry_isExpired() {
        BlacklistEntry entry = makeTemporaryEntry(PAST_EXPIRY);
        assertTrue("Ban with past expiry must be expired", entry.isExpired());
    }

    @Test
    public void temporaryBan_nullExpiry_treatedAsNonExpiring() {
        BlacklistEntry entry = makeTemporaryEntry(null);
        assertFalse("Null expiry on temporary ban must not be treated as expired", entry.isExpired());
    }

    @Test
    public void permanentActiveEntry_isEffectivelyActive() {
        assertTrue(makePermanentEntry().isEffectivelyActive());
    }

    @Test
    public void softRemovedEntry_isNotEffectivelyActive() {
        BlacklistEntry entry = makePermanentEntry();
        entry.setActive(false);
        assertFalse("Soft-removed (active=false) entry must not be effectively active",
                entry.isEffectivelyActive());
    }

    @Test
    public void expiredTemporaryBan_isNotEffectivelyActive() {
        BlacklistEntry entry = makeTemporaryEntry(PAST_EXPIRY);
        assertTrue("isActive flag is still true", entry.isActive());
        assertFalse("Expired ban must not be effectively active despite isActive=true",
                entry.isEffectivelyActive());
    }

    @Test
    public void newPermanentEntry_hasAllExpectedFields() {
        BlacklistEntry entry = new BlacklistEntry(
                PERSON_CNIC, BlacklistEntry.TYPE_PERSON, ENTITY_NAME,
                REASON, BlacklistEntry.BAN_PERMANENT, null, ADMIN_UID);

        assertEquals(PERSON_CNIC,               entry.getEntityId());
        assertEquals(BlacklistEntry.TYPE_PERSON, entry.getEntityType());
        assertEquals(ENTITY_NAME,               entry.getEntityName());
        assertEquals(REASON,                    entry.getReason());
        assertEquals(BlacklistEntry.BAN_PERMANENT, entry.getBanType());
        assertEquals(ADMIN_UID,                 entry.getAddedBy());
        assertTrue("New entry must default to active",   entry.isActive());
        assertNull("Permanent ban must have null expiry", entry.getExpiryDate());
        assertNotNull("addedAt must be set by constructor", entry.getAddedAt());
    }

    @Test
    public void newTemporaryEntry_hasExpiryAndCorrectBanType() {
        BlacklistEntry entry = makeTemporaryEntry(FUTURE_EXPIRY);
        assertEquals(BlacklistEntry.BAN_TEMPORARY, entry.getBanType());
        assertEquals(FUTURE_EXPIRY, entry.getExpiryDate());
    }

    @Test
    public void blacklistEntry_vehicleType_isStoredCorrectly() {
        BlacklistEntry entry = new BlacklistEntry(
                VEHICLE_PLATE, BlacklistEntry.TYPE_VEHICLE, "Car ABC",
                REASON, BlacklistEntry.BAN_PERMANENT, null, ADMIN_UID);
        assertEquals(BlacklistEntry.TYPE_VEHICLE, entry.getEntityType());
        assertEquals(VEHICLE_PLATE, entry.getEntityId());
    }

    // =========================================================================
    // 2. checkBlacklist — Null / Empty Guard (no Firestore should be touched)
    // =========================================================================

    @Test
    public void checkBlacklist_nullEntityId_doesNotQueryFirestore() {
        service.checkBlacklist(null);
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void checkBlacklist_emptyString_doesNotQueryFirestore() {
        service.checkBlacklist("");
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void checkBlacklist_whitespaceOnly_doesNotQueryFirestore() {
        service.checkBlacklist("   ");
        verify(mockDb, never()).collection(anyString());
    }

    // =========================================================================
    // 3. checkBlacklist — Firestore interaction verification
    // =========================================================================

    @Test
    public void checkBlacklist_validPersonCNIC_queriesBlacklistCollection() {
        stubEmptyBlacklistQuery();

        service.checkBlacklist(PERSON_CNIC);

        verify(mockDb).collection("blacklist");
        verify(mockCollection).whereIn(eq("entityId"), anyList());
        verify(mockQuery).get();
    }

    @Test
    public void checkBlacklist_rawPlateDashed_includesNormalizedFormInQuery() {
        stubEmptyBlacklistQuery();

        service.checkBlacklist(VEHICLE_RAW); // "FT-454"

        // Normalized form removes all non-alphanumeric chars → "FT454"
        verify(mockCollection).whereIn(eq("entityId"),
                argThat(list -> list.contains("FT454")));
    }

    @Test
    public void checkBlacklist_callbackVariant_validId_queriesFirestore() {
        stubEmptyBlacklistQuery();

        service.checkBlacklist(PERSON_CNIC, new BlacklistService.BlacklistCheckCallback() {
            @Override public void onResult(boolean isBlacklisted, BlacklistEntry entry) {}
            @Override public void onError(Exception e) {}
        });

        verify(mockDb).collection("blacklist");
    }

    // =========================================================================
    // 4. removeFromBlacklist
    // =========================================================================

    @Test
    public void removeFromBlacklist_success_setsActiveFalseAndCallsOnSuccess() {
        stubRemoveSuccess();

        AtomicBoolean successCalled = new AtomicBoolean(false);
        service.removeFromBlacklist(ENTRY_DOC_ID, new BlacklistService.BlacklistWriteCallback() {
            @Override public void onSuccess() { successCalled.set(true); }
            @Override public void onError(Exception e) { fail("onError must not fire on success"); }
        });

        assertTrue("onSuccess callback must be invoked", successCalled.get());
        verify(mockDocRef).update("active", false);
    }

    @Test
    public void removeFromBlacklist_onlyUpdatesActiveField_notOtherFields() {
        stubRemoveSuccess();

        service.removeFromBlacklist(ENTRY_DOC_ID, noOpWriteCallback());

        // Exactly one update call with field "active" = false
        verify(mockDocRef, times(1)).update("active", false);
        // Must not set active back to true
        verify(mockDocRef, never()).update(eq("active"), eq(true));
    }

    @Test
    public void removeFromBlacklist_firestoreFailure_propagatesErrorToCallback() {
        RuntimeException networkError = new RuntimeException("Firestore unavailable");
        stubRemoveFailure(networkError);

        AtomicReference<Exception> caughtError = new AtomicReference<>();
        service.removeFromBlacklist(ENTRY_DOC_ID, new BlacklistService.BlacklistWriteCallback() {
            @Override public void onSuccess() { fail("onSuccess must not fire on failure"); }
            @Override public void onError(Exception e) { caughtError.set(e); }
        });

        assertNotNull("Error callback must receive the exception", caughtError.get());
        assertSame(networkError, caughtError.get());
    }

    // =========================================================================
    // 5. addToBlacklist — duplicate guard
    // =========================================================================

    @Test
    public void addToBlacklist_alwaysChecksForDuplicateFirst() {
        stubEmptyBlacklistQuery();

        service.addToBlacklist(PERSON_CNIC, BlacklistEntry.TYPE_PERSON, ENTITY_NAME,
                REASON, BlacklistEntry.BAN_PERMANENT, null, ADMIN_UID, noOpWriteCallback());

        verify(mockDb, atLeastOnce()).collection("blacklist");
        verify(mockCollection).whereIn(eq("entityId"), anyList());
    }

    // =========================================================================
    // 6. Edge Cases — null / invalid IDs
    // =========================================================================

    @Test
    public void checkBlacklist_singleSpace_doesNotQueryFirestore() {
        service.checkBlacklist(" ");
        verify(mockDb, never()).collection(anyString());
    }

    @Test
    public void checkBlacklist_numericOnlyCNIC_queriesCorrectly() {
        stubEmptyBlacklistQuery();
        service.checkBlacklist("1234567890123");
        verify(mockCollection).whereIn(eq("entityId"),
                argThat(list -> list.contains("1234567890123")));
    }

    @Test
    public void blacklistEntry_setActive_changesFlag() {
        BlacklistEntry entry = makePermanentEntry();
        assertTrue(entry.isActive());
        entry.setActive(false);
        assertFalse(entry.isActive());
        entry.setActive(true);
        assertTrue(entry.isActive());
    }

    @Test
    public void blacklistEntry_setEntryId_isReflected() {
        BlacklistEntry entry = makePermanentEntry();
        entry.setEntryId("doc-xyz");
        assertEquals("doc-xyz", entry.getEntryId());
    }

    // =========================================================================
    // Helpers — stub builders
    // =========================================================================

    /** Stubs a blacklist query that returns an empty snapshot (no entries found). */
    @SuppressWarnings("unchecked")
    private void stubEmptyBlacklistQuery() {
        when(mockDb.collection("blacklist")).thenReturn(mockCollection);
        when(mockCollection.whereIn(anyString(), anyList())).thenReturn(mockQuery);
        when(mockQuery.get()).thenReturn(mockQueryTask);
        doAnswer(inv -> {
            ((OnSuccessListener<QuerySnapshot>) inv.getArgument(0)).onSuccess(mockSnapshot);
            return mockQueryTask;
        }).when(mockQueryTask).addOnSuccessListener(any());
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
        when(mockSnapshot.isEmpty()).thenReturn(true);
        when(mockSnapshot.getDocuments()).thenReturn(Collections.emptyList());
    }

    /** Stubs removeFromBlacklist to fire onSuccess synchronously. */
    @SuppressWarnings("unchecked")
    private void stubRemoveSuccess() {
        when(mockDb.collection("blacklist")).thenReturn(mockCollection);
        when(mockCollection.document(ENTRY_DOC_ID)).thenReturn(mockDocRef);
        when(mockDocRef.update("active", false)).thenReturn(mockVoidTask);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(0)).onSuccess(null);
            return mockVoidTask;
        }).when(mockVoidTask).addOnSuccessListener(any());
        when(mockVoidTask.addOnFailureListener(any())).thenReturn(mockVoidTask);
    }

    /** Stubs removeFromBlacklist to fire onFailure synchronously with the given error. */
    @SuppressWarnings("unchecked")
    private void stubRemoveFailure(Exception error) {
        when(mockDb.collection("blacklist")).thenReturn(mockCollection);
        when(mockCollection.document(ENTRY_DOC_ID)).thenReturn(mockDocRef);
        when(mockDocRef.update("active", false)).thenReturn(mockVoidTask);
        when(mockVoidTask.addOnSuccessListener(any())).thenReturn(mockVoidTask);
        doAnswer(inv -> {
            ((OnFailureListener) inv.getArgument(0)).onFailure(error);
            return mockVoidTask;
        }).when(mockVoidTask).addOnFailureListener(any());
    }

    // =========================================================================
    // Helpers — factory methods for BlacklistEntry
    // =========================================================================

    private BlacklistEntry makePermanentEntry() {
        return new BlacklistEntry(PERSON_CNIC, BlacklistEntry.TYPE_PERSON, ENTITY_NAME,
                REASON, BlacklistEntry.BAN_PERMANENT, null, ADMIN_UID);
    }

    private BlacklistEntry makeTemporaryEntry(Timestamp expiry) {
        return new BlacklistEntry(VEHICLE_PLATE, BlacklistEntry.TYPE_VEHICLE, ENTITY_NAME,
                REASON, BlacklistEntry.BAN_TEMPORARY, expiry, ADMIN_UID);
    }

    private BlacklistService.BlacklistWriteCallback noOpWriteCallback() {
        return new BlacklistService.BlacklistWriteCallback() {
            @Override public void onSuccess() {}
            @Override public void onError(Exception e) {}
        };
    }
}
