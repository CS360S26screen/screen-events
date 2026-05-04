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
import com.example.se_proj.models.DeliveryLog;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.models.ZoneAccessLog;
import com.example.se_proj.rules.AuditLogUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays security audit history, overstay alerts, zone access logs (US18), and
 * delivery logs (US21) for administrative review.
 *
 * <h3>Tab structure</h3>
 * <ol>
 *   <li><b>Audit Log</b> — existing {@code access_logs} history with CNIC/hostId search.</li>
 *   <li><b>Overstaying</b> — computed on-the-fly from on-campus requests past end time.</li>
 *   <li><b>Zone Logs</b> — {@code zone_access_logs} (US18): every gate scan with zone and outcome.</li>
 *   <li><b>Delivery Logs</b> — {@code delivery_logs} (US21): rider lifecycle and duration records.</li>
 * </ol>
 *
 * <p>Tabs 3 and 4 map their respective domain models to {@link AuditLog} projections so
 * the existing {@link AuditLogAdapter} can render them without requiring new layout files.</p>
 */
public class AdminAuditActivity extends AppCompatActivity {

    public static final String EXTRA_SHOW_GUARD_NAV = "show_guard_nav";

    private static final int TAB_AUDIT    = 0;
    private static final int TAB_OVERSTAY = 1;
    private static final int TAB_ZONE     = 2;
    private static final int TAB_DELIVERY = 3;

    private ActivityAdminAuditBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private AuditLogAdapter adapter;
    private int currentTab = TAB_AUDIT;

    private ListenerRegistration logsListener;
    private ListenerRegistration searchListener1;
    private ListenerRegistration searchListener2;
    private ListenerRegistration zoneLogsListener;
    private ListenerRegistration deliveryLogsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminAuditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finishWithoutAnimation());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                LogoutUtils.showLogoutConfirmation(this);
                return true;
            }
            return false;
        });

        // Programmatically add tabs 3 and 4 if they are not already in the XML layout
        ensureZoneAndDeliveryTabsExist();

        setupRecyclerView();
        setupTabs();
        setupSearch();
        setupGuardBottomNavigation();
        fetchLogs();
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }

    private void startGuardPortalActivity(Intent intent, boolean finishCurrent) {
        startActivity(intent);
        overridePendingTransition(0, 0);
        if (finishCurrent) {
            finishWithoutAnimation();
        }
    }

    private void setupGuardBottomNavigation() {
        boolean showGuardNav = getIntent().getBooleanExtra(EXTRA_SHOW_GUARD_NAV, false);
        if (!showGuardNav) {
            binding.bottomNavigation.setVisibility(View.GONE);
            return;
        }

        binding.bottomNavigation.setVisibility(View.VISIBLE);
        binding.rvAuditLogs.setClipToPadding(false);
        binding.rvAuditLogs.setPadding(
                binding.rvAuditLogs.getPaddingLeft(),
                binding.rvAuditLogs.getPaddingTop(),
                binding.rvAuditLogs.getPaddingRight(),
                getResources().getDimensionPixelSize(R.dimen.guard_bottom_nav_content_padding)
        );
        binding.bottomNavigation.setSelectedItemId(R.id.nav_logs);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_logs) {
                return true;
            } else if (id == R.id.nav_home) {
                startGuardPortalActivity(new Intent(this, GuardDashboardActivity.class), true);
                return true;
            } else if (id == R.id.nav_adhoc) {
                startGuardPortalActivity(new Intent(this, WalkInRegistrationActivity.class), true);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeAllListeners();
    }

    // =========================================================================
    // Tab management
    // =========================================================================

    /**
     * Adds "Zone Logs" and "Delivery Logs" tabs if the TabLayout has only 2 items.
     * This avoids requiring a layout XML change while still making the feature work.
     */
    private void ensureZoneAndDeliveryTabsExist() {
        if (binding.tabLayout.getTabCount() < 3) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Zone Logs"));
        }
        if (binding.tabLayout.getTabCount() < 4) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Delivery Logs"));
        }
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
                currentTab = tab != null ? tab.getPosition() : TAB_AUDIT;
                // Show search bar only on Audit Log tab
                binding.tilSearch.setVisibility(currentTab == TAB_AUDIT ? View.VISIBLE : View.GONE);
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

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void refreshData() {
        switch (currentTab) {
            case TAB_AUDIT:    fetchLogs();        break;
            case TAB_OVERSTAY: fetchOverstaying();  break;
            case TAB_ZONE:     fetchZoneLogs();     break;
            case TAB_DELIVERY: fetchDeliveryLogs(); break;
        }
    }

    // =========================================================================
    // Tab 0 — Audit Log (unchanged)
    // =========================================================================

    private void fetchLogs() {
        removeSearchListeners();
        removeZoneAndDeliveryListeners();
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
     * Runs two parallel Firestore listeners (by CNIC and by hostId) and merges their results.
     *
     * <p>Same parallel-merge pattern as the original implementation.
     * A two-element boolean array tracks which listener has fired at least once.</p>
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

    private void mergeAndUpdateIfBothDone(List<AuditLog> cnicLogs, List<AuditLog> hostLogs,
                                           boolean[] done) {
        if (done[0] && done[1]) {
            List<AuditLog> merged = AuditLogUtils.mergeAndSortDistinct(cnicLogs, hostLogs);
            adapter.updateData(merged, false);
        }
    }

    // =========================================================================
    // Tab 1 — Overstaying (unchanged)
    // =========================================================================

    private void fetchOverstaying() {
        removeAllNonOverstayListeners();
        LocalDateTime now = LocalDateTime.now();
        db.collection("visitor_requests")
                .whereEqualTo("onCampus", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<VisitorRequest> onCampus = snapshots != null
                            ? snapshots.toObjects(VisitorRequest.class)
                            : new ArrayList<>();
                    List<AuditLog> mappedLogs = new ArrayList<>();
                    for (VisitorRequest r : onCampus) {
                        if (AuditLogUtils.isOverstaying(r, now)) {
                            mappedLogs.add(AuditLogUtils.toOverstayAuditLog(r));
                        }
                    }
                    adapter.updateData(mappedLogs, true);
                });
    }

    // =========================================================================
    // Tab 2 — Zone Access Logs (US18)
    // =========================================================================

    /**
     * Fetches {@code zone_access_logs} in real time and maps each {@link ZoneAccessLog}
     * to an {@link AuditLog} projection so the existing {@link AuditLogAdapter} renders it.
     *
     * <p>Mapping convention:</p>
     * <ul>
     *   <li>{@code visitorName} ← entityName</li>
     *   <li>{@code visitorCNIC} ← entityId</li>
     *   <li>{@code hostId}      ← zoneId (shown as "Host ID" in the card)</li>
     *   <li>{@code action}      ← action (ENTRY / EXIT / DENIED)</li>
     *   <li>{@code reason}      ← outcome + failureReason</li>
     * </ul>
     */
    private void fetchZoneLogs() {
        removeAllNonZoneListeners();
        zoneLogsListener = db.collection("zone_access_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(200)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    List<AuditLog> mapped = new ArrayList<>();
                    if (snapshots != null) {
                        for (ZoneAccessLog zl : snapshots.toObjects(ZoneAccessLog.class)) {
                            mapped.add(zoneLogToAuditLog(zl));
                        }
                    }
                    adapter.updateData(mapped, false);
                });
    }

    /** Projects a {@link ZoneAccessLog} into an {@link AuditLog} for display. */
    private AuditLog zoneLogToAuditLog(ZoneAccessLog zl) {
        String reason = ZoneAccessLog.OUTCOME_SUCCESS.equals(zl.getOutcome())
                ? "Zone: " + zl.getZoneId()
                : zl.getOutcome() + " | " + zl.getFailureReason()
                        + " | Zone: " + zl.getZoneId();
        return new AuditLog(
                zl.getLogId(),
                zl.getEntityName(),
                zl.getEntityId(),
                zl.getZoneId(),
                zl.getAction(),
                reason,
                zl.getGuardId(),
                zl.getTimestamp()
        );
    }

    // =========================================================================
    // Tab 3 — Delivery Logs (US21)
    // =========================================================================

    /**
     * Fetches {@code delivery_logs} in real time and maps each {@link DeliveryLog}
     * to an {@link AuditLog} projection.
     *
     * <p>Mapping convention:</p>
     * <ul>
     *   <li>{@code visitorName} ← riderName</li>
     *   <li>{@code visitorCNIC} ← riderCNIC</li>
     *   <li>{@code hostId}      ← destinationBlock</li>
     *   <li>{@code action}      ← "DELIVERY_" + status.toUpperCase()</li>
     *   <li>{@code reason}      ← vehicle + duration info</li>
     * </ul>
     */
    private void fetchDeliveryLogs() {
        removeAllNonDeliveryListeners();
        deliveryLogsListener = db.collection("delivery_logs")
                .orderBy("entryTime", Query.Direction.DESCENDING)
                .limit(200)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    List<AuditLog> mapped = new ArrayList<>();
                    if (snapshots != null) {
                        for (DeliveryLog dl : snapshots.toObjects(DeliveryLog.class)) {
                            mapped.add(deliveryLogToAuditLog(dl));
                        }
                    }
                    // Overstay rows get the red-tint visual from AuditLogAdapter
                    adapter.updateData(mapped, false);
                });
    }

    /** Projects a {@link DeliveryLog} into an {@link AuditLog} for display. */
    private AuditLog deliveryLogToAuditLog(DeliveryLog dl) {
        String action = "DELIVERY_" + dl.getStatus().toUpperCase(java.util.Locale.ROOT);
        String reason = "Vehicle: " + dl.getVehicleNumber()
                + " | Company: " + dl.getCompanyName()
                + " | Dest: " + dl.getDestinationBlock()
                + " | Host: " + dl.getReceivingHostId()
                + " | Entry: " + dl.getEntryZoneId()
                + " | Exit: " + dl.getExitZoneId()
                + " | Duration: " + dl.getDurationMinutes() + " min"
                + (dl.isOverstayFlagged() ? " ⚠ OVERSTAY" : "");
        // Use entryTime for the timestamp card display
        Timestamp ts = dl.getEntryTime() != null ? dl.getEntryTime() : Timestamp.now();
        return new AuditLog(
                dl.getDeliveryId(),
                dl.getRiderName(),
                dl.getRiderCNIC(),
                dl.getDestinationBlock(),
                action,
                reason,
                dl.getGuardId(),
                ts
        );
    }

    // =========================================================================
    // Listener lifecycle
    // =========================================================================

    private void removeSearchListeners() {
        if (searchListener1 != null) { searchListener1.remove(); searchListener1 = null; }
        if (searchListener2 != null) { searchListener2.remove(); searchListener2 = null; }
    }

    private void removeZoneAndDeliveryListeners() {
        if (zoneLogsListener     != null) { zoneLogsListener.remove();     zoneLogsListener     = null; }
        if (deliveryLogsListener != null) { deliveryLogsListener.remove(); deliveryLogsListener = null; }
    }

    private void removeAllNonOverstayListeners() {
        removeSearchListeners();
        removeZoneAndDeliveryListeners();
        if (logsListener != null) { logsListener.remove(); logsListener = null; }
    }

    private void removeAllNonZoneListeners() {
        removeSearchListeners();
        if (logsListener         != null) { logsListener.remove();         logsListener         = null; }
        if (deliveryLogsListener != null) { deliveryLogsListener.remove(); deliveryLogsListener = null; }
    }

    private void removeAllNonDeliveryListeners() {
        removeSearchListeners();
        if (logsListener     != null) { logsListener.remove();     logsListener     = null; }
        if (zoneLogsListener != null) { zoneLogsListener.remove(); zoneLogsListener = null; }
    }

    private void removeAllListeners() {
        removeSearchListeners();
        removeZoneAndDeliveryListeners();
        if (logsListener != null) { logsListener.remove(); logsListener = null; }
    }
}
