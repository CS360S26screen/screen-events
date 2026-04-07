package com.example.se_proj.models

import com.google.firebase.firestore.DocumentId

/**
 * Domain model representing a visitor access request in the Campus Gate Access System.
 *
 * <p>Each instance maps 1-to-1 with a document in the Firestore {@code visitor_requests}
 * collection. The class serves both pre-scheduled faculty/student requests and ad-hoc
 * walk-in requests created by guards at the gate.</p>
 *
 * <h3>Lifecycle</h3>
 * A request progresses through the statuses defined in
 * {@link com.example.se_proj.rules.RequestStatus}:
 * {@code pending} / {@code pending_adhoc} &rarr; {@code approved} | {@code rejected} | {@code denied} | {@code cancelled}.
 * Once approved, the guard may check the visitor in ({@link #onCampus} = true) and later
 * check them out.
 *
 * <h3>Security Model</h3>
 * <ul>
 *   <li>{@link #creatorId} stores the Firebase Auth UID of the user who created the request
 *       and is used by Firestore security rules to enforce ownership-based access.</li>
 *   <li>{@link #hostId} stores the human-readable roll number or faculty ID used for
 *       display and business-logic queries (e.g. guest-limit checks).</li>
 * </ul>
 *
 * <h3>Public Interface</h3>
 * As a Kotlin {@code data class}, this model exposes generated public methods such as
 * {@code copy()}, {@code equals()}, {@code hashCode()}, {@code toString()}, and component
 * destructuring methods. Property-level docs below define each field contract used by those
 * generated APIs.
 *
 * <h3>Outstanding Issues</h3>
 * The temporal fields ({@code visitDate}, {@code startTime}, {@code endTime},
 * {@code entryTime}, {@code exitTime}) are currently string-based and should migrate to
 * structured timestamp/date types for stronger validation and query safety.
 *
 * @property requestId   Firestore document ID, auto-populated by {@link DocumentId}.
 * @property guestName   Full name of the visitor.
 * @property guestCNIC   13-digit CNIC of the visitor (stored digits-only after normalization).
 * @property purpose     Reason for the visit (e.g. "Meeting", "Student Guest Visit").
 * @property visitDate   Scheduled visit date in {@code dd/MM/yyyy} format.
 * @property startTime   Approved entry window start in {@code HH:mm} format.
 * @property endTime     Approved entry window end in {@code HH:mm} format.
 * @property hostId      Roll number or faculty ID of the campus host.
 * @property creatorId   Firebase Auth UID of the request creator (used by security rules).
 * @property hostType    Role of the host: {@code "faculty"} or {@code "student"}.
 * @property status      Current request status (see {@link com.example.se_proj.rules.RequestStatus}).
 * @property entryTime   Actual entry timestamp set by the guard on check-in, or null.
 * @property exitTime    Actual exit timestamp set by the guard on check-out, or null.
 * @property onCampus    {@code true} while the visitor is physically on campus.
 */
data class VisitorRequest(
    @DocumentId
    val requestId: String = "",
    val guestName: String = "",
    val guestCNIC: String = "",
    val purpose: String = "",
    val visitDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val hostId: String = "",
    val creatorId: String = "",
    val hostType: String = "faculty",
    val status: String = "pending",
    val entryTime: String? = null,
    val exitTime: String? = null,
    val onCampus: Boolean = false
)
