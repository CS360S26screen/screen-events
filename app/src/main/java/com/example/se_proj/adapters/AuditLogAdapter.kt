package com.example.se_proj.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.databinding.ItemAuditLogBinding
import com.example.se_proj.models.AuditLog
import com.example.se_proj.rules.UiFormatUtils

class AuditLogAdapter(
    private var logs: List<AuditLog>,
    private var isOverstayView: Boolean = false
) : RecyclerView.Adapter<AuditLogAdapter.ViewHolder>() {

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

        when (UiFormatUtils.resolveAuditVisualState(log.action, isOverstayView)) {
            UiFormatUtils.AuditVisualState.OVERSTAYING -> {
                holder.binding.cardView.setCardBackgroundColor(Color.parseColor("#FFCDD2")) // Red tint
            }
            UiFormatUtils.AuditVisualState.ENTRY -> {
                holder.binding.cardView.setCardBackgroundColor(Color.parseColor("#C8E6C9")) // Green tint
            }
            UiFormatUtils.AuditVisualState.DENIED -> {
                holder.binding.cardView.setCardBackgroundColor(Color.parseColor("#FFE0B2")) // Orange tint
            }
            UiFormatUtils.AuditVisualState.DEFAULT -> {
                holder.binding.cardView.setCardBackgroundColor(Color.WHITE)
            }
        }
    }

    override fun getItemCount() = logs.size

    fun updateData(newLogs: List<AuditLog>, overstay: Boolean = false) {
        logs = newLogs
        isOverstayView = overstay
        notifyDataSetChanged()
    }
}
