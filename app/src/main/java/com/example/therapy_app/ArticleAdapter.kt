package com.example.therapy_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ArticleAdapter(
    private var articles: List<Article>,
    private val onClick: (Article) -> Unit
) : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {

    inner class ArticleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.articleTitle)
        val summary: TextView = view.findViewById(R.id.articleSummary)
        val image: ImageView = view.findViewById(R.id.articleImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_article, parent, false)
        return ArticleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        val article = articles[position]

        holder.title.text = article.title
        holder.summary.text = article.summary

        // Load image safely
        Glide.with(holder.itemView.context)
            .load(article.imageUrl)
            .centerCrop()
            .into(holder.image)

        holder.itemView.setOnClickListener {
            onClick(article)
        }
    }

    override fun getItemCount(): Int = articles.size

    fun updateList(newList: List<Article>) {
        articles = newList
        notifyDataSetChanged()
    }
}
