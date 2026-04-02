package org.jellyfin.androidtv.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.random.Random

class MadflixMediaBarController(
	private val api: ApiClient,
	private val userViewsRepository: UserViewsRepository,
) {
	suspend fun load(): MadflixMediaBarState = withContext(Dispatchers.IO) {
		val views = userViewsRepository.views.first()

		val allowedViews = views.filterNot(::isExcludedView)
		if (allowedViews.isEmpty()) {
			return@withContext MadflixMediaBarState(isLoading = false)
		}

		val pool = coroutineScope {
			allowedViews
				.shuffled()
				.map { view ->
					async {
						runCatching {
							val latest by api.userLibraryApi.getLatestMedia(
								parentId = view.id,
								fields = ItemRepository.itemFields,
								imageTypeLimit = 1,
								groupItems = true,
								limit = ITEMS_PER_VIEW,
							)

							latest.filter(::isValidHeroItem)
						}.getOrElse { emptyList() }
					}
				}
				.awaitAll()
				.flatten()
		}

		val selected = pool
			.distinctBy { it.id }
			.shuffled(Random(System.currentTimeMillis()))
			.take(HERO_POOL_SIZE)

		MadflixMediaBarState(
			isLoading = false,
			items = selected,
		)
	}

	private fun isExcludedView(view: BaseItemDto): Boolean {
		val typeName = view.collectionType?.name?.uppercase().orEmpty()
		val name = view.name.orEmpty().lowercase()

		return typeName == "BOOKS" ||
			typeName == "GAMES" ||
			name.contains("gaming") ||
			name.contains("games") ||
			name.contains("jeux")
	}

	private fun isValidHeroItem(item: BaseItemDto): Boolean {
		val typeName = item.type?.name?.uppercase().orEmpty()
		val collectionTypeName = item.collectionType?.name?.uppercase().orEmpty()

		return item.name.orEmpty().isNotBlank() &&
			hasVisuals(item) &&
			collectionTypeName != "GAMES" &&
			typeName !in setOf(
			"AUDIO",
			"AUDIO_BOOK",
			"BOOK",
			"PLAYLIST",
			"PHOTO",
			"PERSON",
			"EPISODE",
			"COLLECTION_FOLDER",
		)
	}

	private fun hasVisuals(item: BaseItemDto): Boolean {
		return !item.imageTags.isNullOrEmpty() || !item.backdropImageTags.isNullOrEmpty()
	}

	private companion object {
		const val ITEMS_PER_VIEW = 24
		const val HERO_POOL_SIZE = 50
	}
}
