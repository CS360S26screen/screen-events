package com.example.se_proj;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.adapters.BlacklistEntryAdapter;
import com.example.se_proj.models.BlacklistEntry;
import com.example.se_proj.rules.BlacklistService;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class AdminBlacklistActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private BlacklistEntryAdapter adapter;
    private BlacklistService blacklistService;
    private ListenerRegistration listenerReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_blacklist);

        blacklistService = new BlacklistService(db);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvBlacklist);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlacklistEntryAdapter(new ArrayList<>(), this::confirmRemove);
        rv.setAdapter(adapter);

        listenForActiveEntries();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerReg != null) listenerReg.remove();
    }

    private void listenForActiveEntries() {
        View tvEmpty = findViewById(R.id.tvEmpty);
        listenerReg = db.collection("blacklist")
                .whereEqualTo("active", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<BlacklistEntry> entries = snapshots != null
                            ? snapshots.toObjects(BlacklistEntry.class)
                            : new ArrayList<>();
                    entries.sort((a, b) -> {
                        if (a.getAddedAt() == null) return 1;
                        if (b.getAddedAt() == null) return -1;
                        return b.getAddedAt().compareTo(a.getAddedAt());
                    });
                    adapter.updateData(entries);
                    tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void confirmRemove(BlacklistEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Remove from Blacklist")
                .setMessage("Remove \"" + entry.getEntityName() + "\" (" + entry.getEntityId() + ") from the blacklist?")
                .setPositiveButton("Remove", (d, w) ->
                        blacklistService.removeFromBlacklist(entry.getEntryId(),
                                new BlacklistService.BlacklistWriteCallback() {
                                    @Override public void onSuccess() {
                                        Toast.makeText(AdminBlacklistActivity.this, "Removed.", Toast.LENGTH_SHORT).show();
                                    }
                                    @Override public void onError(Exception ex) {
                                        Toast.makeText(AdminBlacklistActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
