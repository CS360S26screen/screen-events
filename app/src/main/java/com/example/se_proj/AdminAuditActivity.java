package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.se_proj.adapters.AuditLogAdapter;
import com.example.se_proj.databinding.ActivityAdminAuditBinding;
import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.AuditLogUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays security audit history and computed overstay alerts for administrative review.
 *
 * <p>Design note: combines repository/listener orchestration with adapter presentation, while
 * delegating merge and overstay logic to {@link AuditLogUtils} (rules-engine extraction).</p>
 *
 * <p>Outstanding issues: search currently runs two live listeners per query and may increase
 * Firestore read costs under sustained typing; debounce/server-side indexing is recommended.</p>
 */
public class AdminAuditActivity extends AppCompatActivity {

    private ActivityAdminAuditBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private AuditLogAdapter adapter;
    private int currentTab = 0;
    private ListenerRegistration logsListener;
    private ListenerRegistration searchListener1;
    private ListenerRegistration searchListener2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminAuditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });

        setupRecyclerView();
        setupTabs();
        setupSearch();
        fetchLogs();
    }

    private void setupRecyclerView() {
        adapter = new AuditLogAdapter(new ArrayList<>(), false);
        binding.rvAuditLogs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAuditLogs.setAdapter(adapter);
    }

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab != null ? tab.getPosition() : 0;
                binding.tilSearch.setVisibility(currentTab == 0 ? View.VISIBLE : View.GONE);
                refreshData();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String searchText = s.toString().trim();
                if (searchText.isEmpty()) {
                    fetchLogs();
                } else {
                    searchLogs(searchText);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void refreshData() {
        if (currentTab == 0) fetchLogs(); else fetchOverstaying();
    }

    private void removeSearchListeners() {
        if (searchListener1 != null) { searchListener1.remove(); searchListener1 = null; }
        if (searchListener2 != null) { searchListener2.remove(); searchListener2 = null; }
    }

    private void fetchLogs() {
        removeSearchListeners();
        if (logsListener != null) logsListener.remove();

        logsListener = db.collection("access_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    List<AuditLog> logs = snapshots != null
                            ? snapshots.toObjects(AuditLog.class)
                            : new ArrayList<>();
                    adapter.updateData(logs, false);
                });
    }

    /**
     * Runs two parallel Firestore listeners (by CNIC and by hostId) and merges their results
     * once both have returned at least one snapshot.
     *
     * <p>A two-element boolean array tracks completion: {@code done[0]} = CNIC query done,
     * {@code done[1]} = hostId query done. This is the standard Java workaround for the
     * Kotlin nested-function + closure pattern.</p>
     */
    private void searchLogs(String text) {
        if (logsListener != null) { logsListener.remove(); logsListener = null; }
        removeSearchListeners();

        List<AuditLog> cnicLogs = new ArrayList<>();
        List<AuditLog> hostLogs = new ArrayList<>();
        boolean[] done = {false, false};

        searchListener1 = db.collection("access_logs")
                .whereEqualTo("visitorCNIC", text)
                .addSnapshotListener((snaps, e) -> {
                    if (e != null) return;
                    cnicLogs.clear();
                    if (snaps != null) cnicLogs.addAll(snaps.toObjects(AuditLog.class));
                    done[0] = true;
                    mergeAndUpdateIfBothDone(cnicLogs, hostLogs, done);
                });

        searchListener2 = db.collection("access_logs")
                .whereEqualTo("hostId", text)
                .addSnapshotListener((snaps, e) -> {
                    if (e != null) return;
                    hostLogs.clear();
                    if (snaps != null) hostLogs.addAll(snaps.toObjects(AuditLog.class));
                    done[1] = true;
                    mergeAndUpdateIfBothDone(cnicLogs, hostLogs, done);
                });
    }

    private void mergeAndUpdateIfBothDone(List<AuditLog> cnicLogs,
                                           List<AuditLog> hostLogs,
                                           boolean[] done) {
        if (done[0] && done[1]) {
            List<AuditLog> merged = AuditLogUtils.mergeAndSortDistinct(cnicLogs, hostLogs);
            adapter.updateData(merged, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logsListener != null) logsListener.remove();
        removeSearchListeners();
    }

    private void fetchOverstaying() {
        LocalDateTime now = LocalDateTime.now();
        db.collection("visitor_requests")
                .whereEqualTo("onCampus", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<VisitorRequest> overstayingRequests = snapshots != null
                            ? snapshots.toObjects(VisitorRequest.class)
                            : new ArrayList<>();

                    List<AuditLog> mappedLogs = new ArrayList<>();
                    for (VisitorRequest r : overstayingRequests) {
                        if (AuditLogUtils.isOverstaying(r, now)) {
                            mappedLogs.add(AuditLogUtils.toOverstayAuditLog(r));
                        }
                    }
                    adapter.updateData(mappedLogs, true);
                });
    }
}
