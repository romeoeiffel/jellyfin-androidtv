package org.jellyfin.androidtv.ui.home

import org.jellyfin.sdk.model.api.BaseItemDto

data class MadflixMediaBarState(
    val isLoading: Boolean = true,
    val items: List<BaseItemDto> = emptyList(),
)
