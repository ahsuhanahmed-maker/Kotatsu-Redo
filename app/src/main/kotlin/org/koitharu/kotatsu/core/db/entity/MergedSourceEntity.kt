package org.koitharu.kotatsu.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.koitharu.kotatsu.core.db.TABLE_MERGED_SOURCES

@Entity(tableName = TABLE_MERGED_SOURCES)
data class MergedSourceEntity(
	@PrimaryKey
	@ColumnInfo(name = "manga_id")
	val mangaId: Long,

	@ColumnInfo(name = "fallback_manga_id")
	val fallbackMangaId: Long,
)
