package com.michaldrabik.ui_my_movies.watchlist

import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_base.BaseViewModel
import com.michaldrabik.ui_base.images.MovieImagesProvider
import com.michaldrabik.ui_base.utilities.ActionEvent
import com.michaldrabik.ui_base.utilities.extensions.findReplace
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType.POSTER
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_my_movies.watchlist.cases.WatchlistLoadMoviesCase
import com.michaldrabik.ui_my_movies.watchlist.cases.WatchlistSortOrderCase
import com.michaldrabik.ui_my_movies.watchlist.recycler.WatchlistListItem
import kotlinx.coroutines.launch
import javax.inject.Inject

class WatchlistViewModel @Inject constructor(
  private val sortOrderCase: WatchlistSortOrderCase,
  private val loadMoviesCase: WatchlistLoadMoviesCase,
  private val imagesProvider: MovieImagesProvider
) : BaseViewModel<WatchlistUiModel>() {

  fun loadMovies(resetScroll: Boolean = false) {
    viewModelScope.launch {
      val items = loadMoviesCase.loadMovies().map {
        val image = imagesProvider.findCachedImage(it, POSTER)
        val translation = loadMoviesCase.loadTranslation(it)
        WatchlistListItem(it, image, false, translation)
      }
      uiState = WatchlistUiModel(items = items, scrollToTop = ActionEvent(resetScroll))
    }
  }

  fun loadSortOrder() {
    viewModelScope.launch {
      val sortOrder = sortOrderCase.loadSortOrder()
      uiState = WatchlistUiModel(sortOrder = ActionEvent(sortOrder))
    }
  }

  fun loadMissingImage(item: WatchlistListItem, force: Boolean) {

    fun updateItem(new: WatchlistListItem) {
      val currentItems = uiState?.items?.toMutableList()
      currentItems?.findReplace(new) { it.isSameAs(new) }
      uiState = uiState?.copy(items = currentItems)
    }

    viewModelScope.launch {
      updateItem(item.copy(isLoading = true))
      try {
        val image = imagesProvider.loadRemoteImage(item.movie, item.image.type, force)
        updateItem(item.copy(isLoading = false, image = image))
      } catch (t: Throwable) {
        updateItem(item.copy(isLoading = false, image = Image.createUnavailable(item.image.type)))
      }
    }
  }

  fun setSortOrder(sortOrder: SortOrder) {
    viewModelScope.launch {
      sortOrderCase.setSortOrder(sortOrder)
      loadMovies(resetScroll = true)
    }
  }
}
