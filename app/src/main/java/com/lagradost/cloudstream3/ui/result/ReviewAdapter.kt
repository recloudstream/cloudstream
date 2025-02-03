package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.RatingFormat
import com.lagradost.cloudstream3.ReviewResponse
import com.lagradost.cloudstream3.databinding.ResultReviewBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewAdapter :
    ListAdapter<ReviewResponse, ReviewAdapter.ReviewAdapterHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewAdapterHolder {
        val binding =
            ResultReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewAdapterHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewAdapterHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class ReviewAdapterHolder(
        private val binding: ResultReviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: ReviewResponse) {
            val context = binding.root.context ?: return

            binding.apply {
                setReviewText(card)
                setReviewTitle(card)
                handleSpoiler(card)
                setReviewDate(card)
                setReviewAuthor(card)
                loadReviewAvatar(card)
                setReviewTags(card, context)
                handleReviewClick(card, context)
            }
        }

        private fun ResultReviewBinding.setReviewText(card: ReviewResponse) {
            reviewBody.text = card.content?.let {
                if (it.length > 300) it.take(300) + "..." else it
            } ?: ""
        }

        private fun ResultReviewBinding.setReviewTitle(card: ReviewResponse) {
            reviewTitle.text = card.title ?: ""
            reviewTitle.isVisible = reviewTitle.text.isNotEmpty()
        }

        private fun ResultReviewBinding.handleSpoiler(card: ReviewResponse) {
            if (card.isSpoiler) {
                var isSpoilerRevealed = false
                reviewBody.isVisible = false
                reviewTitle.isVisible = false
                spoilerText.isVisible = true
                spoilerText.setOnClickListener {
                    isSpoilerRevealed = !isSpoilerRevealed
                    if (isSpoilerRevealed) {
                        reviewBody.isVisible = true
                        reviewTitle.isVisible = true
                    } else {
                        reviewBody.isVisible = false
                        reviewTitle.isVisible = false
                    }
                }
            }
        }

        private fun ResultReviewBinding.setReviewDate(card: ReviewResponse) {
            card.timestamp?.let {
                reviewTime.text = SimpleDateFormat.getDateInstance(
                    SimpleDateFormat.LONG,
                    Locale.getDefault()
                ).format(Date(it))
            }
        }

        private fun ResultReviewBinding.setReviewAuthor(card: ReviewResponse) {
            reviewAuthor.text = card.author
        }

        private fun ResultReviewBinding.loadReviewAvatar(card: ReviewResponse) {
            reviewImage.loadImage(card.avatarUrl, card.avatarHeaders)
        }

        private fun ResultReviewBinding.setReviewTags(
            card: ReviewResponse,
            context: Context
        ) {
            reviewTags.removeAllViews()

            card.rating?.let {
                reviewTags.addChip(
                    context,
                    context.getString(R.string.overall_rating_format).format(
                        it.formatRating(context, card.ratingFormat)
                    ),
                    R.style.ChipReviewAlt,
                    R.attr.primaryGrayBackground
                )
            }

            card.ratings?.forEach { (rating, category) ->
                reviewTags.addChip(
                    context,
                    "$category ${rating.formatRating(context, card.ratingFormat)}",
                    R.style.ChipReview,
                    R.attr.textColor
                )
            }
        }

        private fun Number.formatRating(
            context: Context,
            format: RatingFormat
        ): String {
            return when (format) {
                RatingFormat.STAR -> "$this★"
                RatingFormat.OUT_OF_10 -> "$this/10"
                RatingFormat.OUT_OF_100 -> "$this/100"
                RatingFormat.PERCENT -> "${this.toInt()}%"
                RatingFormat.POSITIVE_NEGATIVE -> {
                    if (this.toInt() > 0) {
                        context.getString(R.string.positive_review)
                    } else context.getString(R.string.negative_review)
                }
            }
        }

        private fun ViewGroup.addChip(
            context: Context,
            text: String,
            style: Int,
            textColor: Int
        ) {
            val chipDrawable = ChipDrawable.createFromAttributes(context, null, 0, style)
            val chip = Chip(context).apply {
                setText(text)
                setChipDrawable(chipDrawable)
                isChecked = false
                isCheckable = false
                isFocusable = false
                isClickable = false
                setTextColor(context.colorFromAttribute(textColor))
            }
            addView(chip)
        }

        private fun ResultReviewBinding.handleReviewClick(
            card: ReviewResponse,
            context: Context
        ) {
            reviewBody.setOnClickListener {
                val builder = AlertDialog.Builder(context).apply {
                    setMessage(card.content)
                    setTitle(card.title ?: card.author ?: card.rating?.let {
                        context.getString(R.string.overall_rating_format).format(
                            it.formatRating(context, card.ratingFormat)
                        )
                    })
                }
                builder.show()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ReviewResponse>() {
        override fun areItemsTheSame(
            oldItem: ReviewResponse,
            newItem: ReviewResponse
        ): Boolean = oldItem == newItem

        override fun areContentsTheSame(
            oldItem: ReviewResponse,
            newItem: ReviewResponse
        ): Boolean = oldItem == newItem
    }
}