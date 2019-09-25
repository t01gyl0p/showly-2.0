package com.michaldrabik.storage.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seasons")
data class Season(
  @PrimaryKey @ColumnInfo(name = "id") var id: Long,
  @ColumnInfo(name = "id_show") var showId: Long,
  @ColumnInfo(name = "season_number") var seasonNumber: Int,
  @ColumnInfo(name = "season_overview") var seasonOverview: String,
  @ColumnInfo(name = "episodes_count") var episodesCount: Int,
  @ColumnInfo(name = "episodes_aired_count") var episodesAiredCount: Int,
  @ColumnInfo(name = "is_watched") var isWatched: Boolean
)