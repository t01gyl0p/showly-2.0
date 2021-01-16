package com.michaldrabik.ui_episodes

import androidx.room.withTransaction
import com.michaldrabik.common.di.AppScope
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.storage.database.AppDatabase
import com.michaldrabik.storage.database.model.EpisodesSyncLog
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.SeasonBundle
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_repository.mappers.Mappers
import com.michaldrabik.ui_repository.shows.ShowsRepository
import timber.log.Timber
import javax.inject.Inject
import com.michaldrabik.storage.database.model.Episode as EpisodeDb
import com.michaldrabik.storage.database.model.Season as SeasonDb

@AppScope
class EpisodesManager @Inject constructor(
  private val showsRepository: ShowsRepository,
  private val database: AppDatabase,
  private val mappers: Mappers
) {

  suspend fun getWatchedSeasonsIds(show: Show) =
    database.seasonsDao().getAllWatchedIdsForShows(listOf(show.traktId))

  suspend fun getWatchedEpisodesIds(show: Show) =
    database.episodesDao().getAllWatchedIdsForShows(listOf(show.traktId))

  suspend fun setSeasonWatched(seasonBundle: SeasonBundle): List<Episode> {
    val toAdd = mutableListOf<EpisodeDb>()
    database.withTransaction {
      val (season, show) = seasonBundle

      val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, true)
      val localSeason = database.seasonsDao().getById(season.ids.trakt.id)
      if (localSeason == null) {
        database.seasonsDao().upsert(listOf(dbSeason))
      }

      val episodes = database.episodesDao().getAllForSeason(season.ids.trakt.id).filter { it.isWatched }

      season.episodes.forEach { ep ->
        if (episodes.none { it.idTrakt == ep.ids.trakt.id }) {
          val dbEpisode = mappers.episode.toDatabase(ep, season, show.ids.trakt, true)
          toAdd.add(dbEpisode)
        }
      }

      database.episodesDao().upsert(toAdd)
      database.seasonsDao().update(listOf(dbSeason))
      database.myShowsDao().updateTimestamp(show.traktId, nowUtcMillis())
    }
    return toAdd.map { mappers.episode.fromDatabase(it) }
  }

  suspend fun setSeasonUnwatched(seasonBundle: SeasonBundle) {
    database.withTransaction {
      val (season, show) = seasonBundle

      val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, false)
      val watchedEpisodes = database.episodesDao().getAllForSeason(season.ids.trakt.id).filter { it.isWatched }
      val toSet = watchedEpisodes.map { it.copy(isWatched = false) }

      val isShowFollowed = showsRepository.myShows.load(show.ids.trakt) != null

      when {
        isShowFollowed -> {
          database.episodesDao().upsert(toSet)
          database.seasonsDao().update(listOf(dbSeason))
        }
        else -> {
          database.episodesDao().delete(toSet)
          database.seasonsDao().delete(listOf(dbSeason))
        }
      }
    }
  }

  suspend fun setEpisodeWatched(episodeId: Long, seasonId: Long, showId: IdTrakt) {
    val episodeDb = database.episodesDao().getAllForSeason(seasonId).find { it.idTrakt == episodeId }!!
    val seasonDb = database.seasonsDao().getById(seasonId)!!
    val show = showsRepository.myShows.load(showId)!!
    setEpisodeWatched(
      EpisodeBundle(
        mappers.episode.fromDatabase(episodeDb),
        mappers.season.fromDatabase(seasonDb),
        show
      )
    )
  }

  suspend fun setEpisodeWatched(episodeBundle: EpisodeBundle) {
    database.withTransaction {
      val (episode, season, show) = episodeBundle

      val dbEpisode = mappers.episode.toDatabase(episode, season, show.ids.trakt, true)
      val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, false)

      val localSeason = database.seasonsDao().getById(season.ids.trakt.id)
      if (localSeason == null) {
        database.seasonsDao().upsert(listOf(dbSeason))
      }
      database.episodesDao().upsert(listOf(dbEpisode))
      database.myShowsDao().updateTimestamp(show.traktId, nowUtcMillis())
      onEpisodeSet(season, show)
    }
  }

  suspend fun setEpisodeUnwatched(episodeBundle: EpisodeBundle) {
    database.withTransaction {
      val (episode, season, show) = episodeBundle

      val isShowFollowed = showsRepository.myShows.load(show.ids.trakt) != null
      val dbEpisode = mappers.episode.toDatabase(episode, season, show.ids.trakt, true)

      when {
        isShowFollowed -> database.episodesDao().upsert(listOf(dbEpisode.copy(isWatched = false)))
        else -> database.episodesDao().delete(listOf(dbEpisode))
      }

      onEpisodeSet(season, show)
    }
  }

  suspend fun setAllUnwatched(show: Show) {
    database.withTransaction {
      val watchedEpisodes = database.episodesDao().getAllByShowId(show.traktId)
      val watchedSeasons = database.seasonsDao().getAllByShowId(show.traktId)

      val updateEpisodes = watchedEpisodes.map { it.copy(isWatched = false) }
      val updateSeasons = watchedSeasons.map { it.copy(isWatched = false) }

      database.episodesDao().upsert(updateEpisodes)
      database.seasonsDao().update(updateSeasons)
    }
  }

  suspend fun invalidateSeasons(show: Show, newSeasons: List<Season>) {
    if (newSeasons.isEmpty()) return

    val localSeasons = database.seasonsDao().getAllByShowId(show.traktId)
    val localEpisodes = database.episodesDao().getAllByShowId(show.traktId)

    val seasonsToAdd = mutableListOf<SeasonDb>()
    val episodesToAdd = mutableListOf<EpisodeDb>()

    newSeasons.forEach { newSeason ->
      val localSeason = localSeasons.find { it.seasonNumber == newSeason.number }
      val seasonDb = mappers.season.toDatabase(
        newSeason,
        show.ids.trakt,
        localSeason?.isWatched ?: false
      )
      seasonsToAdd.add(seasonDb)

      newSeason.episodes.forEach { newEpisode ->
        val localEpisode = localEpisodes.find { it.episodeNumber == newEpisode.number && it.seasonNumber == newEpisode.season }
        val episodeDb = mappers.episode.toDatabase(
          newEpisode,
          newSeason,
          show.ids.trakt,
          localEpisode?.isWatched ?: false
        )
        episodesToAdd.add(episodeDb)
      }
    }

    database.withTransaction {
      database.episodesDao().deleteAllForShow(show.traktId)
      database.seasonsDao().deleteAllForShow(show.traktId)

      database.seasonsDao().upsert(seasonsToAdd)
      database.episodesDao().upsert(episodesToAdd)

      Timber.d("Episodes updated: ${episodesToAdd.size} Seasons updated: ${seasonsToAdd.size}")
    }

    database.episodesSyncLogDao().upsert(EpisodesSyncLog(show.traktId, nowUtcMillis()))
  }

  private suspend fun onEpisodeSet(season: Season, show: Show) {
    val localEpisodes = database.episodesDao().getAllForSeason(season.ids.trakt.id)
    val isWatched = localEpisodes.count { it.isWatched } == season.episodeCount
    val dbSeason = mappers.season.toDatabase(season, show.ids.trakt, isWatched)
    database.seasonsDao().update(listOf(dbSeason))
  }
}
