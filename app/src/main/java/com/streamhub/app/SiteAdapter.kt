package com.streamhub.app

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.streamhub.app.databinding.ItemSiteBinding

class SiteAdapter(
    private val sites: List<Site>,
    private val onClick: (Site) -> Unit
) : RecyclerView.Adapter<SiteAdapter.SiteHolder>() {

    class SiteHolder(val binding: ItemSiteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteHolder {
        val binding = ItemSiteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SiteHolder(binding)
    }

    override fun onBindViewHolder(holder: SiteHolder, position: Int) {
        val site = sites[position]
        holder.binding.avatar.text = site.avatar
        holder.binding.avatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(site.color)
        }
        holder.binding.name.text = site.name
        holder.binding.domain.text = site.domain
        holder.binding.root.setOnClickListener { onClick(site) }
    }

    override fun getItemCount(): Int = sites.size
}
