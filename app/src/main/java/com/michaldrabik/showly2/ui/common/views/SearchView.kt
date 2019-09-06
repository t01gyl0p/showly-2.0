package com.michaldrabik.showly2.ui.common.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.michaldrabik.showly2.R
import com.michaldrabik.showly2.ui.common.SearchViewBehaviour
import com.michaldrabik.showly2.utilities.extensions.dimenToPx

class SearchView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), CoordinatorLayout.AttachedBehavior {

  init {
    inflate(context, R.layout.view_search, this)
  }

  override fun getBehavior() = SearchViewBehaviour(context.dimenToPx(R.dimen.spaceSmall))
}