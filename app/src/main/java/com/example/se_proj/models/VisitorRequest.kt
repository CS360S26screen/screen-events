package com.example.se_proj.models

import com.google.firebase.firestore.DocumentId

data class VisitorRequest(
    @DocumentId
    val requestId: String = "",
    val guestName: String = "",
    val guestCNIC: String = "",
    val purpose: String = "",
    val visitDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val hostId: String = "",         // Roll Number/Faculty ID for logic
    val creatorId: String = "",      // Firebase Auth UID for security rules
    val hostType: String = "faculty", // "faculty" or "student"
    val status: String = "pending",
    val entryTime: String? = null,
    val exitTime: String? = null,
    val onCampus: Boolean = false
)
