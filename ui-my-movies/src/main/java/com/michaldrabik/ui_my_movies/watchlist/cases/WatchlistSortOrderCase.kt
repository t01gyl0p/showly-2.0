package com.michaldrabik.ui_my_movies.watchlist.cases

import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class WatchlistSortOrderCase @Inject constructor(
  private val settingsRepository: SettingsRepository
) {

  fun setSortOrder(sortOrder: SortOrder, sortType: SortType) {
    settingsRepository.sortSettings.watchlistMoviesSortOrder = sortOrder
    settingsRepository.sortSettings.watchlistMoviesSortType = sortType
  }

  fun loadSortOrder() = Pair(
    settingsRepository.sortSettings.watchlistMoviesSortOrder,
    settingsRepository.sortSettings.watchlistMoviesSortType
  )
}
