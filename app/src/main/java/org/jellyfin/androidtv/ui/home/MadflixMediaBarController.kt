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
import java.net.HttpURLConnection
import java.net.URL
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

		val priorityIds = loadPriorityIds()

		val pool = coroutineScope {
			allowedViews
				.shuffled(Random(System.nanoTime()))
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

		val dedupedPool = pool.distinctBy { it.id }
		val poolById = dedupedPool.associateBy { normalizeItemId(it.id?.toString()) }

		val priorityItems = priorityIds
			.mapNotNull { id -> poolById[normalizeItemId(id)] }
			.distinctBy { it.id }
			.take(MAX_PRIORITY_ITEMS)

		val priorityIdSet = priorityItems
			.map { normalizeItemId(it.id?.toString()) }
			.toSet()

		val remainingItems = dedupedPool
			.filterNot { item -> normalizeItemId(item.id?.toString()) in priorityIdSet }
			.shuffled(Random(System.nanoTime()))
			.take((HERO_POOL_SIZE - priorityItems.size).coerceAtLeast(0))

		val selected = (priorityItems + remainingItems)
			.distinctBy { it.id }
			.take(HERO_POOL_SIZE)

		MadflixMediaBarState(
			isLoading = false,
			items = selected,
		)
	}

	private suspend fun loadPriorityIds(): List<String> = withContext(Dispatchers.IO) {
		val baseUrl = api.baseUrl?.trimEnd('/') ?: return@withContext emptyList()
		val url = "$baseUrl$PRIORITY_LIST_PATH"

		runCatching {
			val connection = URL(url).openConnection() as HttpURLConnection
			connection.requestMethod = "GET"
			connection.connectTimeout = 4000
			connection.readTimeout = 4000

			val code = connection.responseCode
			if (code !in 200..299) return@runCatching emptyList<String>()

			val body = connection.inputStream.bufferedReader().use { it.readText() }

			body.lineSequence()
				.map { line -> line.substringBefore("#").trim() }
				.map(::normalizeItemId)
				.filter { it.length == 32 }
				.distinct()
				.take(MAX_PRIORITY_ITEMS)
				.toList()
		}.getOrElse { emptyList() }
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
		const val ITEMS_PER_VIEW = 60
		const val HERO_POOL_SIZE = 50
		const val MAX_PRIORITY_ITEMS = 4
		const val PRIORITY_LIST_PATH = "/web/list-androidtv.txt"
	}

	private fun normalizeItemId(raw: String?): String {
		return raw
			.orEmpty()
			.filter { it.isLetterOrDigit() }
			.lowercase()
	}
}
