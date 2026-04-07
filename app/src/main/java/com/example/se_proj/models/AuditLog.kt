package com.example.se_proj.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class AuditLog(
    @DocumentId
    val id: String = "",
    val visitorName: String = "",
    val visitorCNIC: String = "",
    val hostId: String = "",
    val action: String = "", // Entry, Exit, Denied, ADMIN_APPROVED, etc.
    val reason: String = "", // if denied or override
    val creatorId: String = "", // UID of the person who took the action
    val timestamp: Timestamp = Timestamp.now()
)
