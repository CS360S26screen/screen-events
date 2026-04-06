package com.example.se_proj.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.R
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.UiFormatUtils

class VisitorRequestAdapter(
    private var requests: List<VisitorRequest>,
    private val onApproveClick: (VisitorRequest) -> Unit,
    private val onRejectClick: (VisitorRequest) -> Unit
) : RecyclerView.Adapter<VisitorRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGuestName: TextView = view.findViewById(R.id.tvGuestName)
        val tvPurpose: TextView = view.findViewById(R.id.tvPurpose)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val btnApprove: Button = view.findViewById(R.id.btnApprove)
        val btnReject: Button = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_visitor_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        holder.tvGuestName.text = request.guestName
        holder.tvPurpose.text = request.purpose
        holder.tvDate.text = UiFormatUtils.formatVisitorDate(request.visitDate)

        holder.btnApprove.setOnClickListener { onApproveClick(request) }
        holder.btnReject.setOnClickListener { onRejectClick(request) }
    }

    override fun getItemCount() = requests.size

    fun updateData(newRequests: List<VisitorRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
