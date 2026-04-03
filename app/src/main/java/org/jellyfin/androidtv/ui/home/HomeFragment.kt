package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject
import androidx.compose.ui.focus.FocusRequester

class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()
	private val api by inject<ApiClient>()
	private val userViewsRepository by inject<UserViewsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val mediaBarFocusRequester = remember { FocusRequester() }
		var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }

		val mediaBarController = remember { MadflixMediaBarController(api, userViewsRepository) }
		var mediaBarState by remember { mutableStateOf(MadflixMediaBarState()) }

		var selectedRowPosition by remember { mutableIntStateOf(0) }
		var initialHeroFocusDone by remember { mutableStateOf(false) }
		var heroHasFocus by remember { mutableStateOf(true) }

		val heroOffset = when {
			heroHasFocus -> 0.dp
			selectedRowPosition == 0 -> (-150).dp
			else -> (-520).dp
		}

		val heroVisible = heroHasFocus || selectedRowPosition == 0

		val rowsOffset = when {
			heroHasFocus -> 440.dp
			selectedRowPosition == 0 -> 300.dp
			else -> 0.dp
		}

		LaunchedEffect(Unit) {
			mediaBarState = mediaBarController.load()
		}

		LaunchedEffect(mediaBarState.items, rowsSupportFragment, initialHeroFocusDone) {
			if (
				!initialHeroFocusDone &&
				mediaBarState.items.isNotEmpty() &&
				rowsSupportFragment != null
			) {
				delay(120)

				rowsSupportFragment?.selectedPosition = 0
				rowsSupportFragment?.verticalGridView?.post {
					rowsSupportFragment?.verticalGridView?.clearFocus()
					heroHasFocus = true
					mediaBarFocusRequester.requestFocus()
				}
			}
		}

		JellyfinTheme {
			Box(
				modifier = Modifier.fillMaxSize()
			) {
				if (heroVisible) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.align(Alignment.TopCenter)
							.offset(y = heroOffset)
							.zIndex(0f)
					) {
						MadflixMediaBarHero(
							state = mediaBarState,
							autoAdvance = true,
							focusRequester = mediaBarFocusRequester,
							onMoveDownToRows = {
								rowsSupportFragment?.focusSelectedRowFromHero()
								heroHasFocus = false
							},
							onHeroFocusChanged = {
								heroHasFocus = true
								initialHeroFocusDone = true
							},
							modifier = Modifier.fillMaxWidth()
						)
					}
				}

				AndroidFragment<HomeRowsFragment>(
					modifier = Modifier
						.fillMaxSize()
						.offset(y = rowsOffset)
						.zIndex(1f),
					onUpdate = { fragment ->
						rowsSupportFragment = fragment

						fragment.onMoveUpFromFirstRow = {
							fragment.verticalGridView?.clearFocus()
							selectedRowPosition = 0
							heroHasFocus = true
							mediaBarFocusRequester.requestFocus()
						}

						fragment.onSelectedRowPositionChanged = { position ->
							selectedRowPosition = position.coerceAtLeast(0)

							if (fragment.verticalGridView?.hasFocus() == true) {
								heroHasFocus = false
							}
						}
					}
				)

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.align(Alignment.TopCenter)
						.zIndex(2f)
				) {
					MainToolbar(
						activeButton = MainToolbarActiveButton.Home,
						downFocusRequester = mediaBarFocusRequester,
					)
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}
}
