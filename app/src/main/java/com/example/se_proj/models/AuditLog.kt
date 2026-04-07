package com.example.se_proj.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Domain model representing an immutable audit log entry in the Campus Gate Access System.
 *
 * <p>Each instance maps to a document in the Firestore {@code access_logs} collection.
 * Audit logs are created by guards (entry/exit/denied events) and admins (approve/reject
 * actions) to maintain a tamper-evident record of all gate activity.</p>
 *
 * <p>Logs are append-only; Firestore security rules prohibit updates and deletes on the
 * {@code access_logs} collection to guarantee immutability.</p>
 *
 * <h3>Public Interface</h3>
 * This Kotlin {@code data class} provides generated public methods
 * ({@code copy()}, {@code equals()}, {@code hashCode()}, {@code toString()}, and component
 * methods). The properties documented below define the contract for those APIs.
 *
 * <h3>Outstanding Issues</h3>
 * The {@code action} field is still free-form text; replacing it with a constrained enum or
 * sealed hierarchy would reduce typo-driven filtering/reporting bugs.
 *
 * @property id          Firestore document ID, auto-populated by {@link DocumentId}.
 * @property visitorName Display name of the visitor involved in the event.
 * @property visitorCNIC 13-digit CNIC of the visitor (used for search/filtering).
 * @property hostId      Roll number or faculty ID of the campus host linked to the visit.
 * @property action      Event type: {@code "Entry"}, {@code "Exit"}, {@code "Denied"},
 *                       {@code "ADMIN_APPROVED"}, {@code "ADMIN_REJECTED"}, or {@code "OVERSTAYING"}.
 * @property reason      Additional context (e.g. denial reason, "Supervisor Override").
 *                       Empty string when not applicable.
 * @property creatorId   Firebase Auth UID of the guard or admin who recorded the event.
 * @property timestamp   Server-side timestamp of when the event was logged.
 */
data class AuditLog(
    @DocumentId
    val id: String = "",
    val visitorName: String = "",
    val visitorCNIC: String = "",
    val hostId: String = "",
    val action: String = "",
    val reason: String = "",
    val creatorId: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
