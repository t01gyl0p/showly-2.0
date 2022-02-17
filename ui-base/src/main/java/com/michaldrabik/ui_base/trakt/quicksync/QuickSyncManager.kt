package com.michaldrabik.ui_base.trakt.quicksync

import androidx.work.WorkManager
import com.michaldrabik.common.Mode
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.database.AppDatabase
import com.michaldrabik.data_local.database.model.TraktSyncQueue
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Operation
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.ui_base.utilities.extensions.runTransaction
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickSyncManager @Inject constructor(
    private val userTraktManager: UserTraktManager,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val workManager: WorkManager
) {

  suspend fun scheduleEpisodes(
    episodesIds: List<Long>,
    showId: Long? = null,
    clearProgress: Boolean = false
  ) {
    if (!ensureQuickSync() && !(clearProgress && ensureQuickRemove())) {
      return
    }

    val time = nowUtcMillis()
    val items = episodesIds.map { TraktSyncQueue.createEpisode(it, showId, time, time, clearProgress) }
    database.traktSyncQueueDao().insert(items)
    Timber.d("Episodes added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleMovies(moviesIds: List<Long>) {
    if (!ensureQuickSync()) return

    val time = nowUtcMillis()
    val items = moviesIds.map { TraktSyncQueue.createMovie(it, time, time) }
    database.traktSyncQueueDao().insert(items)
    Timber.d("Movies added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleShowsWatchlist(showsIds: List<Long>) {
    if (!ensureQuickSync()) return

    val time = nowUtcMillis()
    val items = showsIds.map { TraktSyncQueue.createShowWatchlist(it, time, time) }
    database.traktSyncQueueDao().insert(items)
    Timber.d("Shows added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleMoviesWatchlist(moviesIds: List<Long>) {
    if (!ensureQuickSync()) return

    val time = nowUtcMillis()
    val items = moviesIds.map { TraktSyncQueue.createMovieWatchlist(it, time, time) }
    database.traktSyncQueueDao().insert(items)
    Timber.d("Movies added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleAddToList(idTrakt: Long, idList: Long, type: Mode) {
    if (!ensureQuickSync()) return

    val time = nowUtcMillis()
    val item = when (type) {
      Mode.SHOWS -> TraktSyncQueue.createListShow(idTrakt, idList, Operation.ADD, time, time)
      Mode.MOVIES -> TraktSyncQueue.createListMovie(idTrakt, idList, Operation.ADD, time, time)
    }

    val itemType = when (type) {
      Mode.SHOWS -> Type.LIST_ITEM_SHOW
      Mode.MOVIES -> Type.LIST_ITEM_MOVIE
    }

    database.runTransaction {
      traktSyncQueueDao().delete(idTrakt, idList, itemType.slug, Operation.ADD.slug)
      val count = traktSyncQueueDao().delete(idTrakt, idList, itemType.slug, Operation.REMOVE.slug)
      if (count == 0) {
        traktSyncQueueDao().insert(listOf(item))
        Timber.d("Added ${type.type} list item into add to list queue.")
      }
    }

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleRemoveFromList(idTrakt: Long, idList: Long, type: Mode) {
    if (!ensureQuickRemove()) return

    val time = nowUtcMillis()
    val item = when (type) {
      Mode.SHOWS -> TraktSyncQueue.createListShow(idTrakt, idList, Operation.REMOVE, time, time)
      Mode.MOVIES -> TraktSyncQueue.createListMovie(idTrakt, idList, Operation.REMOVE, time, time)
    }

    val itemType = when (type) {
      Mode.SHOWS -> Type.LIST_ITEM_SHOW
      Mode.MOVIES -> Type.LIST_ITEM_MOVIE
    }

    database.runTransaction {
      traktSyncQueueDao().delete(idTrakt, idList, itemType.slug, Operation.REMOVE.slug)
      val count = traktSyncQueueDao().delete(idTrakt, idList, itemType.slug, Operation.ADD.slug)
      if (count == 0 && ensureQuickRemove()) {
        traktSyncQueueDao().insert(listOf(item))
        Timber.d("Added ${type.type} list item into remove from list queue.")
      }
    }

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleHidden(idTrakt: Long, type: Mode, operation: Operation) {
    if (!ensureQuickSync()) return

    val time = nowUtcMillis()
    val item = when (type) {
      Mode.SHOWS -> TraktSyncQueue.createHiddenShow(idTrakt, operation, time, time)
      Mode.MOVIES -> TraktSyncQueue.createHiddenMovie(idTrakt, operation, time, time)
    }

    database.traktSyncQueueDao().insert(listOf(item))

    when (type) {
      Mode.SHOWS -> Timber.d("Hidden show added into sync queue. #$idTrakt")
      Mode.MOVIES -> Timber.d("Hidden movie added into sync queue. #$idTrakt")
    }

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun clearEpisodes(episodesIds: List<Long>) {
    if (!ensureQuickRemove()) return

    val count = database.traktSyncQueueDao().deleteAll(episodesIds, Type.EPISODE.slug)
    Timber.d("Episodes removed from sync queue. Count: $count")
  }

  suspend fun clearEpisodes() {
    if (!ensureQuickRemove()) return

    database.traktSyncQueueDao().deleteAll(Type.EPISODE.slug)
    Timber.d("Episodes removed from sync queue.")
  }

  suspend fun clearMovies(moviesIds: List<Long>) {
    if (!ensureQuickRemove()) return

    database.traktSyncQueueDao().deleteAll(moviesIds, Type.MOVIE.slug)
    Timber.d("Movies removed from sync queue. Count: ${moviesIds.size}")
  }

  suspend fun clearWatchlistShows(showsIds: List<Long>) {
    if (!ensureQuickRemove()) return

    database.traktSyncQueueDao().deleteAll(showsIds, Type.SHOW_WATCHLIST.slug)
    Timber.d("Shows removed from sync queue. Count: ${showsIds.size}")
  }

  suspend fun clearWatchlistMovies(moviesIds: List<Long>) {
    if (!ensureQuickRemove()) return

    database.traktSyncQueueDao().deleteAll(moviesIds, Type.MOVIE_WATCHLIST.slug)
    Timber.d("Movies removed from sync queue. Count: ${moviesIds.size}")
  }

  suspend fun clearHiddenShows(ids: List<Long>) {
    if (!ensureQuickRemove()) return

    database.traktSyncQueueDao().deleteAll(ids, Type.HIDDEN_SHOW.slug)
    Timber.d("Hidden shows removed from sync queue. Count: ${ids.size}")
  }

  suspend fun clearHiddenMovies(ids: List<Long>) {
    if (!ensureQuickRemove()) return

    database.traktSyncQueueDao().deleteAll(ids, Type.HIDDEN_MOVIE.slug)
    Timber.d("Hidden shows removed from sync queue. Count: ${ids.size}")
  }

  suspend fun isAnyScheduled(): Boolean {
    if (!ensureAuthorized()) return false

    return database.traktSyncQueueDao().getAll().isNotEmpty()
  }

  private suspend fun ensureQuickSync(): Boolean {
    if (!ensureAuthorized()) return false

    val settings = settingsRepository.load()
    if (!settings.traktQuickSyncEnabled) {
      Timber.d("Quick Sync is disabled. Skipping...")
      return false
    }
    return true
  }

  private suspend fun ensureQuickRemove(): Boolean {
    if (!ensureAuthorized()) return false

    val settings = settingsRepository.load()
    if (!settings.traktQuickRemoveEnabled) {
      Timber.d("Quick Remove is disabled. Skipping...")
      return false
    }
    return true
  }

  private suspend fun ensureAuthorized(): Boolean {
    if (!userTraktManager.isAuthorized()) {
      Timber.d("User not logged into Trakt. Skipping...")
      return false
    }
    return true
  }
}
