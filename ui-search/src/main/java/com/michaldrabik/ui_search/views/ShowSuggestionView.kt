package com.michaldrabik.ui_search.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.michaldrabik.ui_base.common.views.ShowView
import com.michaldrabik.ui_base.utilities.extensions.capitalizeWords
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_search.R
import com.michaldrabik.ui_search.recycler.SearchListItem
import kotlinx.android.synthetic.main.view_suggestion_search.view.*

@SuppressLint("SetTextI18n")
class ShowSuggestionView : ShowView<SearchListItem> {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    inflate(context, R.layout.view_suggestion_search, this)
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    suggestionRoot.onClick { itemClickListener?.invoke(item) }
  }

  override val imageView: ImageView = suggestionImage
  override val placeholderView: ImageView = suggestionPlaceholder

  private lateinit var item: SearchListItem

  override fun bind(
    item: SearchListItem,
    missingImageListener: ((SearchListItem, Boolean) -> Unit)?
  ) {
    clear()
    this.item = item

    val translationTitle = item.translation?.title
    suggestionTitle.text =
      if (translationTitle.isNullOrBlank()) item.show.title
      else translationTitle.capitalizeWords()

    val translationOverview = item.translation?.overview
    suggestionDescription.text =
      if (translationOverview.isNullOrBlank()) item.show.overview
      else translationOverview

    suggestionNetwork.text =
      if (item.show.year > 0) context.getString(R.string.textNetwork, item.show.network, item.show.year.toString())
      else String.format("%s", item.show.network)

    suggestionDescription.visibleIf(item.show.overview.isNotBlank())
    suggestionNetwork.visibleIf(item.show.network.isNotBlank())
    loadImage(item, missingImageListener)
  }

  private fun clear() {
    suggestionTitle.text = ""
    suggestionDescription.text = ""
    suggestionNetwork.text = ""
    suggestionPlaceholder.gone()
    Glide.with(this).clear(suggestionImage)
  }
}
