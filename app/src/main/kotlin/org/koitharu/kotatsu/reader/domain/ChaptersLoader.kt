package org.koitharu.kotatsu.reader.domain

import android.util.LongSparseArray
import androidx.annotation.CheckResult
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import javax.inject.Inject

private const val PAGES_TRIM_THRESHOLD = 120

@ViewModelScoped
class ChaptersLoader @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val mangaDataRepository: MangaDataRepository,
) {

		private val chapters = LongSparseArray<MangaChapter>()
	private val chapterPages = ChapterPages()
	private val mutex = Mutex()
	private var currentMangaId: Long? = null

	val size: Int
		get() = chapters.size()

	suspend fun init(manga: MangaDetails) = mutex.withLock {
		chapters.clear()
		currentMangaId = manga.id
		manga.allChapters.forEach {
			chapters.put(it.id, it)
		}
	}

	suspend fun loadPrevNextChapter(manga: MangaDetails, currentId: Long, isNext: Boolean): Boolean {
		val chapters = manga.allChapters
		val predicate: (MangaChapter) -> Boolean = { it.id == currentId }
		val index = if (isNext) chapters.indexOfFirst(predicate) else chapters.indexOfLast(predicate)
		if (index == -1) return false
		val newChapter = chapters.getOrNull(if (isNext) index + 1 else index - 1) ?: return false
		val newPages = loadChapter(newChapter.id)
		mutex.withLock {
			if (chapterPages.chaptersSize > 1) {
				// trim pages
				if (chapterPages.size > PAGES_TRIM_THRESHOLD) {
					if (isNext) {
						chapterPages.removeFirst()
					} else {
						chapterPages.removeLast()
					}
				}
			}
			if (isNext) {
				chapterPages.addLast(newChapter.id, newPages)
			} else {
				chapterPages.addFirst(newChapter.id, newPages)
			}
		}
		return true
	}

	@CheckResult
	suspend fun loadSingleChapter(chapterId: Long): Boolean {
		val pages = loadChapter(chapterId)
		return mutex.withLock {
			chapterPages.clear()
			chapterPages.addLast(chapterId, pages)
			pages.isNotEmpty()
		}
	}

	fun peekChapter(chapterId: Long): MangaChapter? = chapters[chapterId]

	fun hasPages(chapterId: Long): Boolean {
		return chapterId in chapterPages
	}

	fun getPages(chapterId: Long): List<MangaPage> = synchronized(chapterPages) {
		return chapterPages.subList(chapterId).map { it.toMangaPage() }
	}

	fun getPagesCount(chapterId: Long): Int {
		return chapterPages.size(chapterId)
	}

	fun last() = chapterPages.last()

	fun first() = chapterPages.first()

	fun snapshot() = chapterPages.toList()

	private suspend fun loadChapter(chapterId: Long): List<ReaderPage> {
		val chapter = checkNotNull(chapters[chapterId]) { "Requested chapter not found" }
		return try {
			val repo = mangaRepositoryFactory.create(chapter.source)
			repo.getPages(chapter).mapIndexed { index, page ->
				ReaderPage(page, index, chapterId)
			}
		} catch (e: Exception) {
			loadFallbackChapter(chapter, e)
		}
	}

	private suspend fun loadFallbackChapter(chapter: MangaChapter, cause: Exception): List<ReaderPage> {
		val mangaId = currentMangaId ?: throw cause
		val fallbackManga = mangaDataRepository.findMergedManga(mangaId, true) ?: throw cause
		val fallbackChapter = findFallbackChapter(chapter, fallbackManga) ?: throw cause
		val repo = mangaRepositoryFactory.create(fallbackChapter.source)
		return repo.getPages(fallbackChapter).mapIndexed { index, page ->
			ReaderPage(page, index, chapter.id)
		}
	}

	private fun findFallbackChapter(chapter: MangaChapter, fallbackManga: Manga): MangaChapter? {
		val chapters = fallbackManga.chapters.orEmpty()
		if (chapters.isEmpty()) {
			return null
		}
		val sameBranch = chapters.filter { it.branch == chapter.branch }
		return sameBranch.findByNumber(chapter.volume, chapter.number)
			?: chapters.findByNumber(chapter.volume, chapter.number)
			?: sameBranch.firstOrNull()
			?: chapters.firstOrNull()
	}

	private fun List<MangaChapter>.findByNumber(volume: Int, number: Float): MangaChapter? {
		return if (number <= 0f) {
			null
		} else {
			firstOrNull { it.volume == volume && it.number == number }
		}
	}
}
