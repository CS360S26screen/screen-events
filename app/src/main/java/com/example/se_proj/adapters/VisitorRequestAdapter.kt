package com.example.se_proj.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.R
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.UiFormatUtils

/**
 * RecyclerView adapter for pending visitor requests shown on the admin approval screen.
 *
 * Design note: classic Adapter/ViewHolder pattern with callback injection for approve/reject
 * actions so decision handling stays in the hosting Activity.
 *
 * Outstanding issues: uses `notifyDataSetChanged()` for all updates; consider `DiffUtil` for
 * smoother animations and lower bind cost on large lists.
 */
class VisitorRequestAdapter(
    private var requests: List<VisitorRequest>,
    private val onApproveClick: (VisitorRequest) -> Unit,
    private val onRejectClick: (VisitorRequest) -> Unit
) : RecyclerView.Adapter<VisitorRequestAdapter.ViewHolder>() {

    /** Holds references to each request row's UI controls. */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGuestName: TextView = view.findViewById(R.id.tvGuestName)
        val tvHostInfo: TextView = view.findViewById(R.id.tvHostInfo)
        val tvTimeWindow: TextView = view.findViewById(R.id.tvTimeWindow)
        val chipStatus: TextView = view.findViewById(R.id.chipStatus)
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
        holder.tvHostInfo.text = "Host: ${request.hostId}"
        holder.tvTimeWindow.text = UiFormatUtils.formatFacultyVisitInfo(request)
        holder.chipStatus.text = RequestStatus.normalize(request.status).uppercase()

        holder.btnApprove.setOnClickListener { onApproveClick(request) }
        holder.btnReject.setOnClickListener { onRejectClick(request) }
    }

    override fun getItemCount() = requests.size

    /** Replaces the current data set and refreshes the list rendering. */
    fun updateData(newRequests: List<VisitorRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
