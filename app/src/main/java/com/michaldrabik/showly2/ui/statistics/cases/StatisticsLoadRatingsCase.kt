package com.michaldrabik.showly2.ui.statistics.cases

import com.michaldrabik.showly2.common.images.ShowImagesProvider
import com.michaldrabik.showly2.di.scope.AppScope
import com.michaldrabik.showly2.model.ImageType
import com.michaldrabik.showly2.repository.UserTraktManager
import com.michaldrabik.showly2.repository.rating.RatingsRepository
import com.michaldrabik.showly2.repository.settings.SettingsRepository
import com.michaldrabik.showly2.repository.shows.ShowsRepository
import com.michaldrabik.showly2.ui.statistics.views.ratings.recycler.StatisticsRatingItem
import javax.inject.Inject

@AppScope
class StatisticsLoadRatingsCase @Inject constructor(
  private val userTraktManager: UserTraktManager,
  private val showsRepository: ShowsRepository,
  private val settingsRepository: SettingsRepository,
  private val ratingsRepository: RatingsRepository,
  private val imagesProvider: ShowImagesProvider
) {

  companion object {
    private const val LIMIT = 25
  }

  suspend fun loadRatings(): List<StatisticsRatingItem> {
    if (!userTraktManager.isAuthorized()) {
      return emptyList()
    }

    val token = userTraktManager.checkAuthorization()
    val ratings = ratingsRepository.loadShowsRatings(token.token)
    val includeArchived = settingsRepository.load().archiveShowsIncludeStatistics

    val ratingsIds = ratings.map { it.idTrakt }
    val myShows = showsRepository.myShows.loadAll(ratingsIds)
    val archivedShows = if (includeArchived) showsRepository.archiveShows.loadAll(ratingsIds) else emptyList()
    val allShows = (myShows + archivedShows).distinctBy { it.traktId }

    return ratings
      .filter { rating -> allShows.any { it.traktId == rating.idTrakt.id } }
      .take(LIMIT)
      .map { rating ->
        val show = allShows.first { it.traktId == rating.idTrakt.id }
        StatisticsRatingItem(
          isLoading = false,
          show = show,
          image = imagesProvider.findCachedImage(show, ImageType.POSTER),
          rating = rating
        )
      }.sortedByDescending { it.rating.ratedAt }
  }
}
