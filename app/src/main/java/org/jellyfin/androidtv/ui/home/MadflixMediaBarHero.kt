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

	var selectedIndex by remember(state.items) { mutableIntStateOf(0) }

	LaunchedEffect(state.items) {
		if (selectedIndex !in state.items.indices) selectedIndex = 0
	}

	LaunchedEffect(state.items, autoAdvance) {
		if (!autoAdvance || state.items.size <= 1) return@LaunchedEffect

		while (true) {
			delay(8000)
			selectedIndex =
				if (selectedIndex == state.items.lastIndex) 0 else selectedIndex + 1
		}
	}

	val item = state.items[selectedIndex]
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
