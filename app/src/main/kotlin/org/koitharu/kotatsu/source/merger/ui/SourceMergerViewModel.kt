package org.koitharu.kotatsu.source.merger.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.alternatives.domain.AlternativesUseCase
import org.koitharu.kotatsu.alternatives.ui.MangaAlternativeModel
import org.koitharu.kotatsu.core.model.chaptersCount
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.source.merger.domain.SourceMergerUseCase
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.ListMode
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.append
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.require
import org.koitharu.kotatsu.list.domain.MangaListMapper
import org.koitharu.kotatsu.list.ui.model.ButtonFooter
import org.koitharu.kotatsu.list.ui.model.EmptyState
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.LoadingFooter
import org.koitharu.kotatsu.list.ui.model.LoadingState
import org.koitharu.kotatsu.list.ui.model.MangaGridModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrDefault
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import javax.inject.Inject

@HiltViewModel
class SourceMergerViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val alternativesUseCase: AlternativesUseCase,
	private val sourceMergerUseCase: SourceMergerUseCase,
	private val mangaListMapper: MangaListMapper,
) : BaseViewModel() {

	val manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	private var includeDisabledSources = MutableStateFlow(false)
	private val results = MutableStateFlow<List<MangaAlternativeModel>>(emptyList())

	private var mergeJob: Job? = null
	private var searchJob: Job? = null

	val onMerged = MutableEventFlow<Manga>()

	val list: StateFlow<List<ListModel>> = combine(
		results,
		isLoading,
		includeDisabledSources,
	) { results, loading, includeDisabled ->
		if (results.isEmpty()) {
			if (loading) {
				listOf(LoadingState)
			} else {
				listOf(
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_search_holder_secondary,
						actionStringRes = 0,
					),
				)
			}
		} else {
			results + LoadingFooter()
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		doSearch(throughDisabledSources = false)
	}

	fun retry() {
		searchJob?.cancel()
		results.value = emptyList()
		includeDisabledSources.value = false
		doSearch(throughDisabledSources = false)
	}

	fun continueSearch() {
		if (includeDisabledSources.value) {
			return
		}
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.Default) {
			includeDisabledSources.value = true
			prevJob?.join()
			doSearch(throughDisabledSources = true)
		}
	}

	fun merge(target: Manga) {
		if (mergeJob?.isActive == true) {
			return
		}
		mergeJob = launchLoadingJob(Dispatchers.Default) {
			sourceMergerUseCase(manga, target)
			onMerged.call(target)
		}
	}

	private fun doSearch(throughDisabledSources: Boolean) {
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			val ref = mangaRepositoryFactory.create(manga.source).getDetails(manga)
			val refCount = ref.chaptersCount()
			alternativesUseCase.invoke(ref, throughDisabledSources)
				.collect {
					val model = MangaAlternativeModel(
						mangaModel = mangaListMapper.toListModel(it, ListMode.GRID) as MangaGridModel,
						referenceChapters = refCount,
						actionTextRes = R.string.merge,
					)
					results.append(model)
			}
		}
	}
}
