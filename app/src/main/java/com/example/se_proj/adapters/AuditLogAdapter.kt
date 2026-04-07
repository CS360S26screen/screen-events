package com.example.se_proj.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.databinding.ItemAuditLogBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.rules.UiFormatUtils

/**
 * RecyclerView adapter for security audit records and computed overstay alerts.
 *
 * Design note: Adapter/ViewHolder pattern with presentation decisions delegated to
 * `UiFormatUtils` for consistent timestamp/state formatting.
 *
 * Outstanding issues: hard-coded color values should be replaced with theme resources to
 * support dark mode and centralized design tokens.
 */
class AuditLogAdapter(
    private var logs: List<AuditLog>,
    private var isOverstayView: Boolean = false
) : RecyclerView.Adapter<AuditLogAdapter.ViewHolder>() {

    /** ViewHolder containing view-binding references for a single audit-log card. */
    class ViewHolder(val binding: ItemAuditLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAuditLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.binding.tvVisitorName.text = log.visitorName
        holder.binding.tvCnic.text = "CNIC: ${log.visitorCNIC}"
        holder.binding.tvAction.text = "Action: ${log.action}"
        holder.binding.tvHostId.text = "Host ID: ${log.hostId}"
        holder.binding.tvTimestamp.text = UiFormatUtils.formatAuditTimestamp(log.timestamp)

        if (log.reason.isNotEmpty()) {
            holder.binding.tvReason.visibility = View.VISIBLE
            holder.binding.tvReason.text = "Reason: ${log.reason}"
        } else {
            holder.binding.tvReason.visibility = View.GONE
        }

        // Feature: Color-coded status for Admin visibility
        when {
            isOverstayView || log.action == "OVERSTAYING" -> {
                holder.binding.root.setCardBackgroundColor(Color.parseColor("#FFCDD2")) // Red tint
            }
            log.action == "Entry" -> {
                holder.binding.root.setCardBackgroundColor(Color.parseColor("#C8E6C9")) // Green tint
            }
            log.action == "Denied" -> {
                holder.binding.root.setCardBackgroundColor(Color.parseColor("#FFE0B2")) // Orange tint
            }
            else -> {
                holder.binding.root.setCardBackgroundColor(Color.WHITE)
            }
        }
    }

    override fun getItemCount() = logs.size

    /** Updates log rows and toggles overstay visual mode for subsequent binds. */
    fun updateData(newLogs: List<AuditLog>, overstay: Boolean = false) {
        logs = newLogs
        isOverstayView = overstay
        notifyDataSetChanged()
    }
}
