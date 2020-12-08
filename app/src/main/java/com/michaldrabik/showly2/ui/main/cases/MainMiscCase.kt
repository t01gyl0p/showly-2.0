package com.michaldrabik.showly2.ui.main.cases

import android.content.Context
import com.michaldrabik.common.Mode
import com.michaldrabik.common.Mode.MOVIES
import com.michaldrabik.common.Mode.SHOWS
import com.michaldrabik.common.di.AppScope
import com.michaldrabik.ui_base.notifications.AnnouncementManager
import com.michaldrabik.ui_repository.RatingsRepository
import com.michaldrabik.ui_repository.SettingsRepository
import javax.inject.Inject

@AppScope
class MainMiscCase @Inject constructor(
  private val ratingsRepository: RatingsRepository,
  private val settingsRepository: SettingsRepository,
  private val announcementManager: AnnouncementManager
) {

  suspend fun refreshAnnouncements(context: Context) = announcementManager.refreshEpisodesAnnouncements(context)

  fun clear() = ratingsRepository.clear()

  fun setMode(mode: Mode) = settingsRepository.setMode(mode)

  fun getMode(): Mode {
    val isMoviesEnabled = settingsRepository.isMoviesEnabled()
    val isMovies = settingsRepository.getMode() == MOVIES
    return if (isMoviesEnabled && isMovies) MOVIES else SHOWS
  }

  fun moviesEnabled() = settingsRepository.isMoviesEnabled()
}
