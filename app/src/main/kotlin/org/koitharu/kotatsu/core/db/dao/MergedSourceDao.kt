package org.koitharu.kotatsu.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.koitharu.kotatsu.core.db.entity.MergedSourceEntity

@Dao
abstract class MergedSourceDao {

	@Query("SELECT fallback_manga_id FROM merged_sources WHERE manga_id = :mangaId")
	abstract suspend fun find(mangaId: Long): Long?

	@Upsert
	abstract suspend fun upsert(entity: MergedSourceEntity)
}
