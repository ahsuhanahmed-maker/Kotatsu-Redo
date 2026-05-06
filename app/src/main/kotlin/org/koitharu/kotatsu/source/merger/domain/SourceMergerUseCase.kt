package org.koitharu.kotatsu.source.merger.domain

import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

class SourceMergerUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val mangaDataRepository: MangaDataRepository,
) {

	suspend operator fun invoke(sourceManga: Manga, targetManga: Manga) {
		val targetDetails = if (targetManga.chapters.isNullOrEmpty()) {
			mangaRepositoryFactory.create(targetManga.source).getDetails(targetManga)
		} else {
			targetManga
		}
		mangaDataRepository.storeManga(targetDetails, replaceExisting = false)
		mangaDataRepository.setMergedSource(sourceManga.id, targetDetails.id)
	}
}
