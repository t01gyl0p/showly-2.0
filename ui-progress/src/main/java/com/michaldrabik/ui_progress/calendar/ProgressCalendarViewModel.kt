package com.michaldrabik.ui_progress.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.michaldrabik.common.Config
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalTimeZone
import com.michaldrabik.ui_base.BaseViewModel
import com.michaldrabik.ui_base.images.ShowImagesProvider
import com.michaldrabik.ui_base.utilities.extensions.findReplace
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_progress.ProgressItem
import com.michaldrabik.ui_progress.R
import com.michaldrabik.ui_progress.calendar.ProgressCalendarViewModel.Section.LATER
import com.michaldrabik.ui_progress.calendar.ProgressCalendarViewModel.Section.NEXT_WEEK
import com.michaldrabik.ui_progress.calendar.ProgressCalendarViewModel.Section.THIS_WEEK
import com.michaldrabik.ui_progress.calendar.ProgressCalendarViewModel.Section.TODAY
import com.michaldrabik.ui_progress.calendar.ProgressCalendarViewModel.Section.TOMORROW
import com.michaldrabik.ui_progress.main.ProgressUiModel
import com.michaldrabik.ui_repository.SettingsRepository
import com.michaldrabik.ui_repository.TranslationsRepository
import kotlinx.coroutines.launch
import org.threeten.bp.DayOfWeek.SUNDAY
import org.threeten.bp.LocalTime.NOON
import javax.inject.Inject

class ProgressCalendarViewModel @Inject constructor(
  private val imagesProvider: ShowImagesProvider,
  private val translationsRepository: TranslationsRepository,
  private val settingsRepository: SettingsRepository
) : BaseViewModel<ProgressCalendarUiModel>() {

  private val language by lazy { settingsRepository.getLanguage() }

  enum class Section(@StringRes val headerRes: Int, val order: Int) {
    TODAY(R.string.textToday, 0),
    TOMORROW(R.string.textTomorrow, 1),
    THIS_WEEK(R.string.textThisWeek, 2),
    NEXT_WEEK(R.string.textNextWeek, 3),
    LATER(R.string.textLater, 4)
  }

  fun handleParentAction(model: ProgressUiModel) {
    val allItems = model.items?.toMutableList() ?: mutableListOf()

    val items = allItems
      .filter { it.upcomingEpisode != Episode.EMPTY }
      .sortedBy { it.upcomingEpisode.firstAired }
      .toMutableList()

    val groupedItems = groupByTime(items)

    uiState = ProgressCalendarUiModel(items = groupedItems)
  }

  private fun groupByTime(items: MutableList<ProgressItem>): List<ProgressItem> {
    val today = nowUtc().toLocalTimeZone()
    val nextWeekStart = today.plusDays(((SUNDAY.value - today.dayOfWeek.value) + 1L))

    val timeMap = mutableMapOf<Section, MutableList<ProgressItem>>()
    val sectionsList = mutableListOf<ProgressItem>()

    items.forEach { item ->
      val time = item.upcomingEpisode.firstAired!!.toLocalTimeZone()
      when {
        time.year == today.year && time.dayOfYear == today.dayOfYear ->
          timeMap.getOrPut(TODAY, { mutableListOf() }).add(item)
        time.dayOfYear == today.plusDays(1).dayOfYear ->
          timeMap.getOrPut(TOMORROW, { mutableListOf() }).add(item)
        time.with(NOON).isBefore(nextWeekStart.with(NOON)) ->
          timeMap.getOrPut(THIS_WEEK, { mutableListOf() }).add(item)
        time.with(NOON).isBefore(nextWeekStart.plusWeeks(1).with(NOON)) ->
          timeMap.getOrPut(NEXT_WEEK, { mutableListOf() }).add(item)
        else ->
          timeMap.getOrPut(LATER, { mutableListOf() }).add(item)
      }
    }

    timeMap.entries
      .sortedBy { it.key.order }
      .forEach { (section, items) ->
        sectionsList.run {
          add(ProgressItem.EMPTY.copy(headerTextResId = section.headerRes))
          addAll(items.toList())
        }
      }

    return sectionsList
  }

  fun findMissingImage(item: ProgressItem, force: Boolean) {
    viewModelScope.launch {
      updateItem(item.copy(isLoading = true))
      try {
        val image = imagesProvider.loadRemoteImage(item.show, item.image.type, force)
        updateItem(item.copy(image = image, isLoading = false))
      } catch (t: Throwable) {
        val unavailable = Image.createUnavailable(item.image.type)
        updateItem(item.copy(image = unavailable, isLoading = false))
      }
    }
  }

  fun findMissingTranslation(item: ProgressItem) {
    if (item.showTranslation != null || language == Config.DEFAULT_LANGUAGE) return
    viewModelScope.launch {
      try {
        val translation = translationsRepository.loadTranslation(item.show, language)
        updateItem(item.copy(showTranslation = translation))
      } catch (error: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(error)
      }
    }
  }

  private fun updateItem(new: ProgressItem) {
    val currentItems = uiState?.items?.toMutableList()
    currentItems?.findReplace(new) { it.isSameAs(new) }
    uiState = ProgressCalendarUiModel(items = currentItems)
  }
}
