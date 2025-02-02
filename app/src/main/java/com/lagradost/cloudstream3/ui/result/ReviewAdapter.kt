package com.lagradost.cloudstream3.ui.result

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
import com.lagradost.cloudstream3.UserReview
import com.lagradost.cloudstream3.databinding.ResultReviewBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewAdapter :
    ListAdapter<UserReview, ReviewAdapter.ReviewAdapterHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewAdapterHolder {
        val binding =
            ResultReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewAdapterHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewAdapterHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class ReviewAdapterHolder(private val binding: ResultReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: UserReview) {
            binding.apply {
                val localContext = this.root.context ?: return

                var reviewText = card.review ?: ""
                if (reviewText.length > 300) {
                    reviewText = reviewText.substring(0, 300) + "..."
                }

                reviewBody.text = reviewText
                reviewTitle.text = card.reviewTitle ?: ""
                reviewTitle.isVisible = reviewTitle.text != ""

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
                            spoilerText.text = localContext.getString(R.string.hide_spoiler)
                        } else {
                            reviewBody.isVisible = false
                            reviewTitle.isVisible = false
                            spoilerText.text = localContext.getString(R.string.reveal_spoiler)
                        }
                    }
                }

                reviewBody.setOnClickListener {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(localContext)
                    builder.setMessage(card.review)
                    val title = card.reviewTitle ?: card.username
                    ?: if (card.rating != null) localContext.getString(
                        R.string.overall_rating_format
                    ).format("${card.rating}★") else null
                    if (title != null)
                        builder.setTitle(title)
                    builder.show()
                }

                val reviewDate = card.reviewDate
                if (reviewDate != null) {
                    reviewTime.text = SimpleDateFormat.getDateInstance(
                        DateFormat.LONG,
                        Locale.getDefault()
                    ).format(Date(reviewDate))
                }

                reviewAuthor.text = card.username

                reviewImage.loadImage(card.avatarUrl, card.avatarHeaders)

                reviewTags.apply {
                    removeAllViews()

                    val context = reviewTags.context

                    card.rating?.let { rating ->
                        val chip = Chip(context)
                        val chipDrawable = ChipDrawable.createFromAttributes(
                            context,
                            null,
                            0,
                            R.style.ChipReviewAlt
                        )
                        chip.setChipDrawable(chipDrawable)
                        chip.text = context.getString(
                            R.string.overall_rating_format
                        ).format(
                            when (card.ratingFormat) {
                                RatingFormat.STAR -> "$rating★"
                                RatingFormat.OUT_OF_10 -> "$rating/10"
                                RatingFormat.OUT_OF_100 -> "$rating/100"
                                RatingFormat.PERCENT -> "${rating.toInt()}%"
                                RatingFormat.POSITIVE_NEGATIVE -> {
                                    if (rating.toInt() > 0) localContext.getString(R.string.positive_review)
                                    else localContext.getString(R.string.negative_review)
                                }
                            }
                        )
                        chip.isChecked = false
                        chip.isCheckable = false
                        chip.isFocusable = false
                        chip.isClickable = false

                        // we set the color in code as it cant be set in style
                        chip.setTextColor(context.colorFromAttribute(R.attr.primaryGrayBackground))
                        addView(chip)
                    }

                    card.ratings?.forEach { (rating, category) ->
                        val chip = Chip(context)
                        val chipDrawable = ChipDrawable.createFromAttributes(
                            context,
                            null,
                            0,
                            R.style.ChipReview
                        )
                        chip.setChipDrawable(chipDrawable)
                        chip.text = when (card.ratingFormat) {
                            RatingFormat.STAR -> "$category $rating★"
                            RatingFormat.OUT_OF_10 -> "$category $rating/10"
                            RatingFormat.OUT_OF_100 -> "$category $rating/100"
                            RatingFormat.PERCENT -> "$category ${rating.toInt()}%"
                            RatingFormat.POSITIVE_NEGATIVE -> {
                                if (rating.toInt() > 0) "$category ${localContext.getString(R.string.positive_review)}"
                                else "$category ${localContext.getString(R.string.negative_review)}"
                            }
                        }
                        chip.isChecked = false
                        chip.isCheckable = false
                        chip.isFocusable = false
                        chip.isClickable = false

                        // we set the color in code as it cant be set in style
                        chip.setTextColor(context.colorFromAttribute(R.attr.textColor))
                        addView(chip)
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<UserReview>() {
        override fun areItemsTheSame(oldItem: UserReview, newItem: UserReview): Boolean =
            oldItem == newItem

        override fun areContentsTheSame(oldItem: UserReview, newItem: UserReview): Boolean =
            oldItem == newItem
    }
}