package com.michaldrabik.ui_movie.sections.ratings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Ratings
import com.michaldrabik.ui_movie.MovieDetailsEvent
import com.michaldrabik.ui_movie.cases.MovieDetailsRatingCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MovieDetailsRatingsViewModel @Inject constructor(
  private val ratingsCase: MovieDetailsRatingCase,
) : ViewModel() {

  private val movieState = MutableStateFlow<Movie?>(null)
  private val ratingsState = MutableStateFlow<Ratings?>(null)

  fun handleEvent(event: MovieDetailsEvent<*>) {
    when (event) {
      is MovieDetailsEvent.MovieLoaded -> loadRatings(event.movie)
      else -> Unit
    }
  }

  private fun loadRatings(movie: Movie) {
    val traktRatings = Ratings(
      trakt = Ratings.Value(String.format(Locale.ENGLISH, "%.1f", movie.rating), false),
      imdb = Ratings.Value(null, true),
      metascore = Ratings.Value(null, true),
      rottenTomatoes = Ratings.Value(null, true)
    )
    viewModelScope.launch {
      movieState.value = movie
      try {
        ratingsState.value = traktRatings
        val ratings = ratingsCase.loadExternalRatings(movie)
        ratingsState.value = ratings
      } catch (error: Throwable) {
        ratingsState.value = traktRatings
        rethrowCancellation(error)
      }
    }
  }

  val uiState = combine(
    ratingsState,
    movieState
  ) { s1, s2 ->
    MovieDetailsRatingsUiState(
      ratings = s1,
      movie = s2
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = MovieDetailsRatingsUiState()
  )
}
