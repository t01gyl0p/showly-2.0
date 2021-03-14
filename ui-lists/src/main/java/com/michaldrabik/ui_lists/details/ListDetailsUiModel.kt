package com.michaldrabik.ui_lists.details

import com.michaldrabik.ui_base.UiModel
import com.michaldrabik.ui_base.utilities.ActionEvent
import com.michaldrabik.ui_lists.lists.recycler.ListsItem
import com.michaldrabik.ui_model.CustomList
import com.michaldrabik.ui_model.SortOrder

data class ListDetailsUiModel(
  val details: CustomList? = null,
  val items: List<ListsItem>? = null,
  val sortOrderEvent: ActionEvent<SortOrder>? = null,
  val deleteEvent: ActionEvent<Boolean>? = null
) : UiModel() {

  override fun update(newModel: UiModel) =
    (newModel as ListDetailsUiModel)
      .copy(
        details = newModel.details ?: details,
        items = newModel.items?.toList() ?: items,
        sortOrderEvent = newModel.sortOrderEvent ?: sortOrderEvent,
        deleteEvent = newModel.deleteEvent ?: deleteEvent
      )
}
