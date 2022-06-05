package com.michaldrabik.ui_show.sections.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_base.Analytics
import com.michaldrabik.ui_base.common.sheets.remove_trakt.RemoveTraktBottomSheet.Mode
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_show.R
import com.michaldrabik.ui_show.ShowDetailsEvent
import com.michaldrabik.ui_show.quick_setup.QuickSetupListItem
import com.michaldrabik.ui_show.sections.seasons.cases.ShowDetailsLoadSeasonsCase
import com.michaldrabik.ui_show.sections.seasons.cases.ShowDetailsQuickProgressCase
import com.michaldrabik.ui_show.sections.seasons.cases.ShowDetailsWatchedSeasonCase
import com.michaldrabik.ui_show.sections.seasons.cases.ShowDetailsWatchedSeasonCase.Result
import com.michaldrabik.ui_show.sections.seasons.helpers.SeasonsCache
import com.michaldrabik.ui_show.sections.seasons.recycler.SeasonListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShowDetailsSeasonsViewModel @Inject constructor(
  private val loadSeasonsCase: ShowDetailsLoadSeasonsCase,
  private val quickProgressCase: ShowDetailsQuickProgressCase,
  private val watchedSeasonCase: ShowDetailsWatchedSeasonCase,
  private val seasonsCache: SeasonsCache,
  private val episodesManager: EpisodesManager,
) : ViewModel(), ChannelsDelegate by DefaultChannelsDelegate() {

  private lateinit var show: Show

  private val loadingState = MutableStateFlow(true)
  private val seasonsState = MutableStateFlow<List<SeasonListItem>?>(null)

  private var areSeasonsLocal = false

  fun handleEvent(event: ShowDetailsEvent<*>) {
    when (event) {
      is ShowDetailsEvent.ShowLoaded -> {
        show = event.show
        loadSeasons()
      }
      is ShowDetailsEvent.RefreshSeasons -> refreshWatchedEpisodes()
      else -> Unit
    }
  }

  private fun loadSeasons() {
    viewModelScope.launch {
      try {
        val (seasons, isLocal) = loadSeasonsCase.loadSeasons(show)
        areSeasonsLocal = isLocal
        val calculated = markWatchedEpisodes(seasons)
        seasonsState.value = calculated
      } catch (error: Throwable) {
        seasonsState.value = emptyList()
      }
    }
  }

  fun setSeasonWatched(
    season: Season,
    isChecked: Boolean
  ) {
    viewModelScope.launch {
      val result = watchedSeasonCase.setSeasonWatched(
        show = show,
        season = season,
        isChecked = isChecked,
        isLocal = areSeasonsLocal
      )
      if (result == Result.REMOVE_FROM_TRAKT) {
        val ids = season.episodes.map { it.ids.trakt }
        val event = ShowDetailsSeasonsEvent.RemoveFromTrakt(R.id.actionShowDetailsFragmentToRemoveTraktProgress, Mode.EPISODE, ids)
        eventChannel.send(event)
      }
      refreshWatchedEpisodes()
    }
  }

  fun setQuickProgress(item: QuickSetupListItem?) {
    viewModelScope.launch {
      if (item == null || !checkSeasonsLoaded()) {
        return@launch
      }

      val seasonItems = seasonsState.value?.toList() ?: emptyList()
      quickProgressCase.setQuickProgress(item, seasonItems, show)
      refreshWatchedEpisodes()

      messageChannel.send(MessageEvent.Info(R.string.textShowQuickProgressDone))
      Analytics.logShowQuickProgress(show)
    }
  }

  fun openSeasonEpisodes(season: SeasonListItem) {
    viewModelScope.launch {
      seasonsCache.setSeasons(show.ids.trakt, seasonsState.value ?: emptyList(), areSeasonsLocal)
      val event = ShowDetailsSeasonsEvent.OpenSeasonEpisodes(show.ids.trakt, season.season.ids.trakt)
      eventChannel.send(event)
    }
  }

  fun refreshWatchedEpisodes() {
    if (!this::show.isInitialized || seasonsState.value == null) {
      return
    }
    viewModelScope.launch {
      val seasonItems = seasonsState.value?.toList() ?: emptyList()
      val calculated = markWatchedEpisodes(seasonItems)
      seasonsState.value = calculated
    }
  }

  private suspend fun markWatchedEpisodes(seasonsList: List<SeasonListItem>?): List<SeasonListItem> =
    coroutineScope {
      val items = mutableListOf<SeasonListItem>()

      val (watchedSeasonsIds, watchedEpisodesIds) = awaitAll(
        async { episodesManager.getWatchedSeasonsIds(show) },
        async { episodesManager.getWatchedEpisodesIds(show) }
      )

      seasonsList?.forEach { item ->
        val isSeasonWatched = watchedSeasonsIds.any { id -> id == item.id }
        val episodes = item.episodes.map { episodeItem ->
          val isEpisodeWatched = watchedEpisodesIds.any { id -> id == episodeItem.id }
          episodeItem.copy(season = item.season, isWatched = isEpisodeWatched)
        }
        val updated = item.copy(episodes = episodes, isWatched = isSeasonWatched)
        items.add(updated)
      }

      items
    }

  private suspend fun checkSeasonsLoaded(): Boolean {
    if (seasonsState.value == null) {
      messageChannel.send(MessageEvent.Info(R.string.errorSeasonsNotLoaded))
      return false
    }
    return true
  }

  val uiState = combine(
    loadingState,
    seasonsState
  ) { s1, s2 ->
    ShowDetailsSeasonsUiState(
      isLoading = s1,
      seasons = s2
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = ShowDetailsSeasonsUiState()
  )
}
