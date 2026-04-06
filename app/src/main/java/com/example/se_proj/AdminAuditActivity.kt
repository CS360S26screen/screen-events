package com.example.se_proj

import android.content.Intent
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
import com.example.se_proj.rules.AuditLogUtils
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.time.LocalDateTime

class AdminAuditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAuditBinding
    private val db = Firebase.firestore
    private lateinit var adapter: AuditLogAdapter
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    FirebaseAuth.getInstance().signOut()
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
        db.collection("access_logs")
            .whereEqualTo("visitorCNIC", text)
            .get()
            .addOnSuccessListener { snaps1 ->
                val logs = snaps1.toObjects(AuditLog::class.java).toMutableList()

                db.collection("access_logs")
                    .whereEqualTo("hostId", text)
                    .get()
                    .addOnSuccessListener { snaps2 ->
                        val mergedLogs = AuditLogUtils.mergeAndSortDistinct(
                            logs,
                            snaps2.toObjects(AuditLog::class.java)
                        )
                        adapter.updateData(mergedLogs)
                    }
            }
    }

    private fun fetchOverstaying() {
        val now = LocalDateTime.now()
        db.collection("visitor_requests")
            .whereEqualTo("onCampus", true)
            .get()
            .addOnSuccessListener { snapshots ->
                val overstayingRequests = snapshots?.toObjects(VisitorRequest::class.java) ?: emptyList()
                val mappedLogs = overstayingRequests
                    .filter { AuditLogUtils.isOverstaying(it, now) }
                    .map { AuditLogUtils.toOverstayAuditLog(it) }
                adapter.updateData(mappedLogs, overstay = true)
            }
    }
}
