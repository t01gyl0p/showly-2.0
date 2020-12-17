package com.michaldrabik.ui_model

data class Translation(
  val title: String,
  val overview: String,
  val language: String,
  val isLocal: Boolean = false
) {

  companion object {
    val EMPTY = Translation("", "", "")
  }

  val hasTitle = title.isNotBlank()
}
