package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.json.JSONObject
import java.net.URL
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import timber.log.Timber

private const val DEBUG_THEME_VIDEO = false
private const val DISABLE_THEME_VIDEO_CACHE = false

private const val HERO_VIDEO_START_DELAY_MS = 1800L

private val themeVideoUrlCache = mutableMapOf<String, String?>()

@Composable
fun MadflixMediaBarHero(
	state: MadflixMediaBarState,
	autoAdvance: Boolean = false,
	focusRequester: FocusRequester,
	onMoveDownToRows: (() -> Unit)? = null,
	onHeroFocusChanged: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	if (state.isLoading || state.items.isEmpty()) return

	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val playbackHelper = koinInject<PlaybackHelper>()
	val playbackLauncher = koinInject<PlaybackLauncher>()
	val context = LocalContext.current

	val userRepository = koinInject<UserRepository>()
	val httpDataSourceFactory = koinInject<HttpDataSource.Factory>()

	var selectedIndex by remember(state.items) { mutableIntStateOf(0) }

	LaunchedEffect(state.items) {
		if (selectedIndex !in state.items.indices) selectedIndex = 0
	}

	val item = state.items[selectedIndex]

	var themeVideoUrl by remember(item.id) { mutableStateOf<String?>(null) }
	var themeVideoPending by remember(item.id) { mutableStateOf(false) }
	var showThemeVideo by remember(item.id) { mutableStateOf(false) }
	var themeVideoPlaying by remember(item.id) { mutableStateOf(false) }

	LaunchedEffect(item.id) {
		showThemeVideo = false
		themeVideoPlaying = false
		themeVideoPending = true
		themeVideoUrl = null

		val currentItemId = item.id?.toString() ?: return@LaunchedEffect
		val userId = userRepository.currentUser.filterNotNull().first().id.toString()

		val resolvedUrl = try {
			fetchThemeVideoHlsUrl(
				api = api,
				userId = userId,
				itemId = currentItemId,
			)
		} catch (t: Throwable) {
			Timber.tag("MadflixThemeVideo").e(
				t,
				"resolve failed itemId=%s name=%s",
				currentItemId,
				item.name.orEmpty()
			)
			null
		}

		themeVideoUrl = resolvedUrl

		if (DEBUG_THEME_VIDEO) {
			Timber.tag("MadflixThemeVideo").d(
				"resolved itemId=%s name=%s resolvedUrl=%s",
				item.id,
				item.name.orEmpty(),
				resolvedUrl
			)
		}

		if (resolvedUrl != null) {
			delay(HERO_VIDEO_START_DELAY_MS)
			showThemeVideo = true
			if (DEBUG_THEME_VIDEO) {
				Timber.tag("MadflixThemeVideo").d(
					"showThemeVideo=true itemId=%s name=%s",
					item.id,
					item.name.orEmpty()
				)
			}
		} else {
			themeVideoPending = false
			if (DEBUG_THEME_VIDEO) {
				Timber.tag("MadflixThemeVideo").d(
					"no theme video itemId=%s name=%s",
					item.id,
					item.name.orEmpty()
				)
			}
		}
	}

	LaunchedEffect(
		state.items,
		autoAdvance,
		selectedIndex,
		themeVideoPending,
		showThemeVideo,
		themeVideoPlaying,
	) {
		if (!autoAdvance || state.items.size <= 1) return@LaunchedEffect
		if (themeVideoPending || showThemeVideo || themeVideoPlaying) return@LaunchedEffect

		delay(8000)

		if (!themeVideoPending && !showThemeVideo && !themeVideoPlaying) {
			selectedIndex =
				if (selectedIndex == state.items.lastIndex) 0 else selectedIndex + 1
		}
	}

	val imageUrl = remember(item, api.baseUrl) { buildHeroImageUrl(api.baseUrl, item) }
	val logoUrl = remember(item, api.baseUrl) { buildHeroLogoUrl(api.baseUrl, item) }

	val primaryButtonContainer = Color.White
	val primaryButtonContent = Color.Black
	val iconCircleContainer = Color.Black.copy(alpha = 0.45f)
	val iconCircleBorder = Color.White.copy(alpha = 0.22f)
	val chevronContent = Color.White.copy(alpha = 0.96f)

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(560.dp)
			.focusGroup()
	) {
		Image(
			painter = rememberAsyncImagePainter(model = imageUrl),
			contentDescription = item.name,
			contentScale = ContentScale.FillBounds,
			modifier = Modifier.fillMaxSize()
		)

		val currentThemeVideoUrl = themeVideoUrl
		if (showThemeVideo && currentThemeVideoUrl != null) {
			HeroThemeVideoPlayer(
				videoUrl = currentThemeVideoUrl,
				httpDataSourceFactory = httpDataSourceFactory,
				onStarted = {
					themeVideoPlaying = true
					themeVideoPending = false
				},
				onEnded = {
					themeVideoPlaying = false
					showThemeVideo = false
					selectedIndex =
						if (selectedIndex == state.items.lastIndex) 0 else selectedIndex + 1
				},
				onError = {
					themeVideoPlaying = false
					themeVideoPending = false
					showThemeVideo = false
					themeVideoUrl = null
				},
			)
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.horizontalGradient(
						colors = listOf(
							Color.Black.copy(alpha = 0.95f),
							Color.Black.copy(alpha = 0.82f),
							Color.Black.copy(alpha = 0.48f),
							Color.Black.copy(alpha = 0.14f),
							Color.Transparent
						)
					)
				)
		)

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						colors = listOf(
							Color.Transparent,
							Color.Transparent,
							Color.Black.copy(alpha = 0.10f),
							Color.Black.copy(alpha = 0.30f),
							Color.Black.copy(alpha = 0.68f)
						)
					)
				)
		)

		Box(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.height(220.dp)
				.background(
					Brush.verticalGradient(
						colors = listOf(
							Color.Transparent,
							Color.Black.copy(alpha = 0.18f),
							Color.Black.copy(alpha = 0.42f),
							Color.Black.copy(alpha = 0.88f)
						)
					)
				)
		)

		Column(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(start = 72.dp, end = 72.dp, bottom = 124.dp)
				.widthIn(max = 820.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			if (logoUrl != null) {
				Image(
					painter = rememberAsyncImagePainter(model = logoUrl),
					contentDescription = item.name,
					contentScale = ContentScale.Fit,
					modifier = Modifier.sizeIn(maxWidth = 460.dp, maxHeight = 126.dp)
				)
			} else {
				Text(
					text = item.name.orEmpty(),
					style = JellyfinTheme.typography.default,
					color = Color.White,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}

			val subtitle = buildString {
				item.productionYear?.let { append(it) }
				val typeName = item.type?.name?.replace('_', ' ').orEmpty()
				if (typeName.isNotBlank()) {
					if (isNotBlank()) append("  •  ")
					append(typeName)
				}
			}

			if (subtitle.isNotBlank()) {
				Text(
					text = subtitle,
					style = JellyfinTheme.typography.default,
					color = Color.White.copy(alpha = 0.86f),
				)
			}

			if (!item.overview.isNullOrBlank()) {
				Text(
					text = item.overview!!,
					modifier = Modifier.widthIn(max = 600.dp),
					style = JellyfinTheme.typography.default.copy(
						fontFamily = FontFamily.SansSerif,
						fontWeight = FontWeight.Medium,
						fontSize = 12.sp,
						lineHeight = 18.sp,
						letterSpacing = (-0.1).sp,
					),
					maxLines = 4,
					overflow = TextOverflow.Ellipsis,
					color = Color.White.copy(alpha = 0.82f),
				)
			}

			val iconButtonModifier = Modifier
				.handleMoveDownToRows(onMoveDownToRows)
				.widthIn(min = 40.dp)
				.sizeIn(minHeight = 40.dp)

			val chevronButtonModifier = Modifier
				.handleMoveDownToRows(onMoveDownToRows)
				.widthIn(min = 18.dp)
				.sizeIn(minHeight = 35.dp)

			Row(
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Button(
					modifier = Modifier
						.handleMoveDownToRows(onMoveDownToRows)
						.sizeIn(minHeight = 40.dp)
						.focusRequester(focusRequester)
						.onFocusChanged { state ->
							if (state.hasFocus || state.isFocused) {
								onHeroFocusChanged()
							}
						},
					onClick = {
						playItem(
							context = context,
							item = item,
							playbackHelper = playbackHelper,
							playbackLauncher = playbackLauncher,
						)
					},
					colors = ButtonDefaults.colors(
						containerColor = primaryButtonContainer,
						contentColor = primaryButtonContent,
					)
				) {
					Text(text = "▶ ${context.getString(R.string.lbl_play)}",
						fontSize = 12.sp
					)
				}

				Button(
					modifier = iconButtonModifier,
					onClick = {
						navigationRepository.navigate(Destinations.itemDetails(item.id))
					},
					colors = ButtonDefaults.colors(
						containerColor = Color.Transparent,
						contentColor = Color.White,
					)
				) {
					Box(
						modifier = Modifier
							.size(35.dp)
							.background(iconCircleContainer, CircleShape)
							.border(1.dp, iconCircleBorder, CircleShape),
						contentAlignment = Alignment.Center
					) {
						Text(
							text = "ⓘ",
							fontSize = 24.sp
						)
					}
				}

				if (state.items.size > 1) {
					Button(
						modifier = chevronButtonModifier,
						onClick = {
							selectedIndex =
								if (selectedIndex == 0) state.items.lastIndex else selectedIndex - 1
						},
						colors = ButtonDefaults.colors(
							containerColor = Color.Transparent,
							contentColor = chevronContent,
						)
					) {
						Text(
							text = "‹",
							fontSize = 34.sp
						)
					}

					Button(
						modifier = chevronButtonModifier,
						onClick = {
							selectedIndex =
								if (selectedIndex == state.items.lastIndex) 0 else selectedIndex + 1
						},
						colors = ButtonDefaults.colors(
							containerColor = Color.Transparent,
							contentColor = chevronContent,
						)
					) {
						Text(
							text = "›",
							fontSize = 34.sp
						)
					}
				}
			}
		}
	}
}

private fun Modifier.handleMoveDownToRows(
	onMoveDownToRows: (() -> Unit)?,
): Modifier = onPreviewKeyEvent { event ->
	if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
		onMoveDownToRows?.invoke()
		return@onPreviewKeyEvent onMoveDownToRows != null
	}

	false
}

private fun playItem(
	context: Context,
	item: BaseItemDto,
	playbackHelper: PlaybackHelper,
	playbackLauncher: PlaybackLauncher,
) {
	playbackHelper.getItemsToPlay(
		context,
		item,
		item.type == BaseItemKind.MOVIE,
		false,
		object : Response<List<BaseItemDto>>() {
			override fun onResponse(response: List<BaseItemDto>) {
				playbackLauncher.launch(context, response)
			}
		}
	)
}

private fun buildHeroImageUrl(baseUrl: String?, item: BaseItemDto): String? {
	val base = baseUrl?.trimEnd('/') ?: return null
	val id = item.id ?: return null

	return when {
		!item.backdropImageTags.isNullOrEmpty() ->
			"$base/Items/$id/Images/Backdrop/0?maxHeight=720&quality=85"

		!item.imageTags.isNullOrEmpty() ->
			"$base/Items/$id/Images/Primary?maxHeight=720&quality=85"

		else -> null
	}
}

private fun buildHeroLogoUrl(baseUrl: String?, item: BaseItemDto): String? {
	val base = baseUrl?.trimEnd('/') ?: return null
	val id = item.id.toString()

	val hasLogo = item.imageTags?.keys?.any { it.name == "LOGO" } == true
	if (!hasLogo) return null

	return "$base/Items/$id/Images/Logo?maxWidth=520&quality=85"
}

private suspend fun fetchThemeVideoHlsUrl(
	api: ApiClient,
	userId: String,
	itemId: String,
): String? = withContext(Dispatchers.IO) {

	val cacheKey = "${api.baseUrl}|$userId|$itemId"

	if (!DISABLE_THEME_VIDEO_CACHE) {
		themeVideoUrlCache[cacheKey]?.let {
			if (DEBUG_THEME_VIDEO) {
				Timber.tag("MadflixThemeVideo").d("cache hit key=%s value=%s", cacheKey, it)
			}
			return@withContext it
		}
	}

	val accessToken = api.accessToken ?: return@withContext null

	val themeParams = mapOf(
		"UserId" to userId,
		"InheritFromParent" to "true"
	)

	val themeMediaUrl = api.createUrl(
		"/Items/$itemId/ThemeMedia",
		themeParams,
		emptyMap(),
		true
	)

	if (DEBUG_THEME_VIDEO) {
		Timber.tag("MadflixThemeVideo").d(
			"ThemeMedia request itemId=%s url=%s",
			itemId,
			themeMediaUrl
		)
	}

	val themeMediaRoot = readAuthenticatedJson(
		url = themeMediaUrl,
		accessToken = accessToken,
		label = "ThemeMedia",
		itemId = itemId,
	)

	val resolvedFromThemeMedia = if (themeMediaRoot != null) {
		val themeVideosResult = themeMediaRoot.optJSONObject("ThemeVideosResult")
		val items = themeVideosResult?.optJSONArray("Items")

		if (items == null || items.length() == 0) {
			null
		} else {
			val themeVideoItem = items.optJSONObject(0)
			if (themeVideoItem != null) {
				buildPlayableThemeVideoUrl(
					api = api,
					themeVideoItem = themeVideoItem,
					accessToken = accessToken,
				)
			} else {
				null
			}
		}
	} else {
		null
	}

	if (DEBUG_THEME_VIDEO) {
		Timber.tag("MadflixThemeVideo").d(
			"ThemeMedia resolved itemId=%s url=%s",
			itemId,
			resolvedFromThemeMedia
		)
	}

	if (resolvedFromThemeMedia != null) {
		if (!DISABLE_THEME_VIDEO_CACHE) {
			themeVideoUrlCache[cacheKey] = resolvedFromThemeMedia
		}
		return@withContext resolvedFromThemeMedia
	}

	val themeVideosUrl = api.createUrl(
		"/Items/$itemId/ThemeVideos",
		themeParams,
		emptyMap(),
		true
	)

	if (DEBUG_THEME_VIDEO) {
		Timber.tag("MadflixThemeVideo").d(
			"ThemeVideos request itemId=%s url=%s",
			itemId,
			themeVideosUrl
		)
	}

	val themeVideosRoot = readAuthenticatedJson(
		url = themeVideosUrl,
		accessToken = accessToken,
		label = "ThemeVideos",
		itemId = itemId,
	)

	val resolvedFromThemeVideos = if (themeVideosRoot != null) {
		val items = themeVideosRoot.optJSONArray("Items")

		if (items == null || items.length() == 0) {
			null
		} else {
			val themeVideoItem = items.optJSONObject(0)
			if (themeVideoItem != null) {
				buildPlayableThemeVideoUrl(
					api = api,
					themeVideoItem = themeVideoItem,
					accessToken = accessToken,
				)
			} else {
				null
			}
		}
	} else {
		null
	}

	if (DEBUG_THEME_VIDEO) {
		Timber.tag("MadflixThemeVideo").d(
			"ThemeVideos resolved itemId=%s url=%s",
			itemId,
			resolvedFromThemeVideos
		)
	}

	if (!DISABLE_THEME_VIDEO_CACHE) {
		themeVideoUrlCache[cacheKey] = resolvedFromThemeVideos
	}

	return@withContext resolvedFromThemeVideos
}


private fun buildPlayableThemeVideoUrl(
	api: ApiClient,
	themeVideoItem: JSONObject,
	accessToken: String,
): String? {
	val mediaSources = themeVideoItem.optJSONArray("MediaSources") ?: return null
	if (mediaSources.length() == 0) return null

	val mediaSource = mediaSources.optJSONObject(0) ?: return null

	val transcodingUrl = mediaSource.optString("TranscodingUrl")
	if (transcodingUrl.isNotBlank()) {
		return api.createUrl(
			transcodingUrl,
			emptyMap(),
			emptyMap(),
			true
		)
	}

	val itemId = themeVideoItem.optString("Id")
	if (itemId.isBlank()) return null

	val mediaSourceId = mediaSource.optString("Id")
	val container = mediaSource.optString("Container")
		.substringBefore(',')
		.lowercase()

	val path = if (container.isNotBlank()) {
		"/Videos/$itemId/stream.$container"
	} else {
		"/Videos/$itemId/stream"
	}

	val params = buildMap {
		put("static", "true")
		put("VideoCodec", "copy")
		put("AudioCodec", "copy")
		put("ApiKey", accessToken)
		if (mediaSourceId.isNotBlank()) {
			put("MediaSourceId", mediaSourceId)
		}
	}

	return api.createUrl(
		path,
		params,
		emptyMap(),
		true
	)
}

private fun readAuthenticatedJson(
	url: String,
	accessToken: String,
	label: String,
	itemId: String,
): JSONObject? {
	return try {
		val connection = URL(url).openConnection() as HttpURLConnection
		connection.requestMethod = "GET"
		connection.connectTimeout = 10000
		connection.readTimeout = 10000
		connection.setRequestProperty("Authorization", "MediaBrowser Token=$accessToken")
		connection.setRequestProperty("Accept", "application/json")

		val code = connection.responseCode
		val stream = if (code in 200..299) connection.inputStream else connection.errorStream
		val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

		if (DEBUG_THEME_VIDEO) {
			Timber.tag("MadflixThemeVideo").d(
				"%s auth response itemId=%s code=%s body=%s",
				label,
				itemId,
				code,
				body.take(800)
			)
		}

		if (code !in 200..299 || body.isBlank()) return null
		JSONObject(body)
	} catch (t: Throwable) {
		Timber.tag("MadflixThemeVideo").e(t, "%s auth failed itemId=%s url=%s", label, itemId, url)
		null
	}
}

@Composable
private fun HeroThemeVideoPlayer(
	videoUrl: String,
	httpDataSourceFactory: HttpDataSource.Factory,
	onStarted: () -> Unit,
	onEnded: () -> Unit,
	onError: () -> Unit,
) {
	val context = LocalContext.current

	val exoPlayer = remember(videoUrl) {
		ExoPlayer.Builder(context)
			.setMediaSourceFactory(
				DefaultMediaSourceFactory(
					DefaultDataSource.Factory(context, httpDataSourceFactory)
				)
			)
			.build()
			.apply {
				volume = 0.25f
				repeatMode = Player.REPEAT_MODE_OFF
				playWhenReady = false
				setMediaItem(MediaItem.fromUri(videoUrl))
				prepare()
			}
	}

	DisposableEffect(exoPlayer) {
		val listener = object : Player.Listener {
			override fun onRenderedFirstFrame() {
				onStarted()
			}

			override fun onPlaybackStateChanged(playbackState: Int) {
				if (playbackState == Player.STATE_ENDED) {
					onEnded()
				}
			}

			override fun onPlayerError(error: PlaybackException) {
				Timber.tag("MadflixThemeVideo").e(error, "player error videoUrl=%s", videoUrl)
				onError()
			}
		}

		exoPlayer.addListener(listener)

		onDispose {
			exoPlayer.removeListener(listener)
			exoPlayer.release()
		}
	}

	AndroidView(
		modifier = Modifier.fillMaxSize(),
		factory = { ctx ->
			PlayerView(ctx).apply {
				setEnableComposeSurfaceSyncWorkaround(true)
				useController = false
				resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
				setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
				setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
				layoutParams = ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT,
				)
				player = exoPlayer

				post {
					requestLayout()
					invalidate()
					exoPlayer.playWhenReady = true
				}
			}
		},
		update = { view ->
			if (view.player !== exoPlayer) {
				view.player = exoPlayer
			}

			view.post {
				view.requestLayout()
				view.invalidate()
				if (!exoPlayer.playWhenReady) {
					exoPlayer.playWhenReady = true
				}
			}
		}
	)
}
