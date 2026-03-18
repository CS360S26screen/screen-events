package com.example.se_proj

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.se_proj.adapters.VisitorRequestAdapter
import com.example.se_proj.databinding.ActivityAdminDashboardBinding
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val db = Firebase.firestore
    private lateinit var adapter: VisitorRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSummaryStats()
        fetchPendingRequests()

        binding.btnViewAudit.setOnClickListener {
            startActivity(Intent(this, AdminAuditActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = VisitorRequestAdapter(
            requests = emptyList(),
            onApproveClick = { request -> handleApprove(request) },
            onRejectClick = { request -> updateRequestStatus(request.requestId, "rejected") }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    private fun setupSummaryStats() {
        // Active Guests Count
        db.collection("visitor_requests")
            .whereEqualTo("onCampus", true)
            .addSnapshotListener { snapshots, _ ->
                val count = snapshots?.size() ?: 0
                binding.tvActiveGuests.text = count.toString()
            }

        // Parking Occupancy
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
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("AdminDashboard", "Listen failed.", e)
                    return@addSnapshotListener
                }
                val pendingList = snapshots?.toObjects(VisitorRequest::class.java) ?: emptyList()
                adapter.updateData(pendingList)
            }
    }

    private fun handleApprove(request: VisitorRequest) {
        if (request.requestId.isEmpty()) return
        updateRequestStatus(request.requestId, "approved")
    }

    private fun updateRequestStatus(requestId: String, newStatus: String) {
        if (requestId.isEmpty()) return
        db.collection("visitor_requests").document(requestId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Request ${newStatus.replaceFirstChar { it.uppercase() }}", Toast.LENGTH_SHORT).show()
            }
    }
}
