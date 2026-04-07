package com.example.se_proj

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.se_proj.adapters.VisitorRequestAdapter
import com.example.se_proj.databinding.ActivityAdminDashboardBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.RequestStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val db = Firebase.firestore
    private lateinit var adapter: VisitorRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        setupRecyclerView()
        setupSummaryStats()
        ensureParkingDocument()
        fetchPendingRequests()

        binding.btnViewAudit.setOnClickListener {
            startActivity(Intent(this, AdminAuditActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = VisitorRequestAdapter(
            requests = emptyList(),
            onApproveClick = { request -> handleApprove(request) },
            onRejectClick = { request -> handleReject(request) }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    private fun setupSummaryStats() {
        db.collection("visitor_requests")
            .whereEqualTo("onCampus", true)
            .addSnapshotListener { snapshots, _ ->
                val count = snapshots?.size() ?: 0
                binding.tvActiveGuests.text = count.toString()
            }

        db.collection("system_metadata").document("parking_status")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val occupancy = snapshot.getLong("currentOccupancy") ?: 0
                    val capacity = snapshot.getLong("maxCapacity") ?: 200
                    binding.tvParkingStatus.text = "$occupancy/$capacity"
                }
            }
    }

    private fun fetchPendingRequests() {
        db.collection("visitor_requests")
            .whereIn("status", listOf(RequestStatus.PENDING, RequestStatus.PENDING_ADHOC))
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("AdminDashboard", "Listen failed.", e)
                    return@addSnapshotListener
                }
                val pendingList = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(VisitorRequest::class.java)?.let { request ->
                        if (request.requestId.isEmpty()) request.copy(requestId = doc.id) else request
                    }
                } ?: emptyList()
                adapter.updateData(pendingList)
            }
    }

    private fun handleApprove(request: VisitorRequest) {
        if (request.requestId.isEmpty()) return
        updateRequestStatus(request, RequestStatus.APPROVED)
    }

    private fun handleReject(request: VisitorRequest) {
        if (request.requestId.isEmpty()) return
        updateRequestStatus(request, RequestStatus.REJECTED)
    }

    private fun updateRequestStatus(request: VisitorRequest, newStatus: String) {
        val requestId = request.requestId
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Error: Request ID is missing", Toast.LENGTH_SHORT).show()
            return
        }
        
        db.collection("visitor_requests").document(requestId)
            .update("status", newStatus)
            .addOnSuccessListener {
                logAdminAction(request, newStatus.uppercase())
                Toast.makeText(this, "Request ${newStatus.replaceFirstChar { it.uppercase() }}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Log.e("AdminDashboard", "Failed to update request status", error)
                Toast.makeText(this, "Permission Denied or Network Error", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logAdminAction(request: VisitorRequest, action: String) {
        val log = AuditLog(
            visitorName = request.guestName,
            visitorCNIC = request.guestCNIC,
            hostId = request.hostId,
            action = "ADMIN_$action",
            reason = "Action taken by Security Admin",
            creatorId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
            timestamp = Timestamp.now()
        )
        db.collection("access_logs").add(log)
    }

    private fun ensureParkingDocument() {
        val docRef = db.collection("system_metadata").document("parking_status")
        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                docRef.set(mapOf("currentOccupancy" to 0L, "maxCapacity" to 200L))
            }
        }
    }
}
