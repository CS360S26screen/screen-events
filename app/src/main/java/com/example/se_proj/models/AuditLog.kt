package com.example.se_proj.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class AuditLog(
    @DocumentId
    val id: String = "",
    val visitorName: String = "",
    val visitorCNIC: String = "",
    val hostId: String = "",
    val action: String = "", // Entry, Exit, Denied
    val reason: String = "", // if denied
    val timestamp: Timestamp = Timestamp.now()
)
