package com.example.se_proj

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.se_proj.adapters.AuditLogAdapter
import com.example.se_proj.databinding.ActivityAdminAuditBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.models.VisitorRequest
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminAuditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAuditBinding
    private val db = Firebase.firestore
    private lateinit var adapter: AuditLogAdapter
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupTabs()
        setupSearch()
        fetchLogs()
    }

    private fun setupRecyclerView() {
        adapter = AuditLogAdapter(emptyList())
        binding.rvAuditLogs.layoutManager = LinearLayoutManager(this)
        binding.rvAuditLogs.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                binding.tilSearch.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
                refreshData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val searchText = s.toString().trim()
                if (searchText.isEmpty()) {
                    fetchLogs()
                } else {
                    searchLogs(searchText)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun refreshData() {
        if (currentTab == 0) fetchLogs() else fetchOverstaying()
    }

    private fun fetchLogs() {
        db.collection("access_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshots ->
                val logs = snapshots?.toObjects(AuditLog::class.java) ?: emptyList()
                adapter.updateData(logs)
            }
    }

    private fun searchLogs(text: String) {
        // Requirement #12: Search by CNIC or Host ID
        // Note: Firestore doesn't support OR queries easily with different fields in a simple way without indexes or multiple queries.
        // For satisfying the requirement "Use .whereEqualTo("visitorCNIC", searchText)", we will perform two searches or filter.
        
        db.collection("access_logs")
            .whereEqualTo("visitorCNIC", text)
            .get()
            .addOnSuccessListener { snaps1 ->
                val logs = snaps1.toObjects(AuditLog::class.java).toMutableList()
                
                db.collection("access_logs")
                    .whereEqualTo("hostId", text)
                    .get()
                    .addOnSuccessListener { snaps2 ->
                        logs.addAll(snaps2.toObjects(AuditLog::class.java))
                        adapter.updateData(logs.distinctBy { it.id }.sortedByDescending { it.timestamp })
                    }
            }
    }

    private fun fetchOverstaying() {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        db.collection("visitor_requests")
            .whereEqualTo("onCampus", true)
            .get()
            .addOnSuccessListener { snapshots ->
                val overstayingRequests = snapshots?.toObjects(VisitorRequest::class.java) ?: emptyList()
                val filteredOverstaying = overstayingRequests.filter { it.endTime < currentTime }
                
                val mappedLogs = filteredOverstaying.map { 
                    AuditLog(
                        visitorName = it.guestName,
                        visitorCNIC = it.guestCNIC,
                        hostId = it.hostId,
                        action = "OVERSTAYING",
                        reason = "Scheduled exit: ${it.endTime}"
                    )
                }
                adapter = AuditLogAdapter(mappedLogs, isOverstayView = true)
                binding.rvAuditLogs.adapter = adapter
            }
    }
}
