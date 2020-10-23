package com.michaldrabik.showly2.common

import com.michaldrabik.common.Config
import com.michaldrabik.common.Config.SHOW_SYNC_COOLDOWN
import com.michaldrabik.common.di.AppScope
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.network.Cloud
import com.michaldrabik.storage.database.AppDatabase
import com.michaldrabik.ui_episodes.EpisodesManager
import com.michaldrabik.ui_model.ShowStatus.CANCELED
import com.michaldrabik.ui_model.ShowStatus.ENDED
import com.michaldrabik.ui_repository.TranslationsRepository
import com.michaldrabik.ui_repository.mappers.Mappers
import com.michaldrabik.ui_repository.shows.ShowsRepository
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.*
import javax.inject.Inject

/**
 * This class is responsible for fetching and syncing missing/updated episodes data for current progress shows.
 */
@AppScope
class ShowsSyncRunner @Inject constructor(
  private val cloud: Cloud,
  private val database: AppDatabase,
  private val mappers: Mappers,
  private val episodesManager: EpisodesManager,
  private val showsRepository: ShowsRepository,
  private val translationsRepository: TranslationsRepository
) {

  companion object {
    private const val DELAY_MS = 250L
  }

  suspend fun run(): Int {
    Timber.i("Sync initialized.")

    val showsToSync = showsRepository.myShows.loadAll()
      .filter { it.status !in arrayOf(ENDED, CANCELED) }

    if (showsToSync.isEmpty()) {
      Timber.i("Nothing to process. Exiting...")
      return 0
    }
    Timber.i("Shows to sync: ${showsToSync.size}.")

    var syncCount = 0
    val syncLog = database.episodesSyncLogDao().getAll()
    showsToSync.forEach { show ->

      val lastSync = syncLog.find { it.idTrakt == show.ids.trakt.id }?.syncedAt ?: 0
      if (nowUtcMillis() - lastSync < SHOW_SYNC_COOLDOWN) {
        Timber.i("${show.title} is on cooldown. No need to sync.")
        return@forEach
      }

      try {
        Timber.i("Syncing ${show.title}(${show.ids.trakt}) show...")
        showsRepository.detailsShow.load(show.ids.trakt, force = true)

        val locale = Locale.getDefault()
        if (locale.language !== Config.DEFAULT_LANGUAGE) {
          translationsRepository.updateLocalTranslation(show, locale)
        }
        syncCount++

        Timber.i("${show.title}(${show.ids.trakt}) show synced.")
      } catch (t: Throwable) {
        Timber.e("${show.title}(${show.ids.trakt}) show sync error. Skipping... \n$t")
      }

      try {
        Timber.i("Syncing ${show.title}(${show.ids.trakt}) episodes...")

        val remoteEpisodes = cloud.traktApi.fetchSeasons(show.ids.trakt.id)
          .map { mappers.season.fromNetwork(it) }
        episodesManager.invalidateEpisodes(show, remoteEpisodes)
        syncCount++

        Timber.i("${show.title}(${show.ids.trakt}) episodes synced.")
      } catch (t: Throwable) {
        Timber.e("${show.title}(${show.ids.trakt}) episodes sync error. Skipping... \n$t")
      } finally {
        delay(DELAY_MS)
      }
    }

    return syncCount
  }
}
