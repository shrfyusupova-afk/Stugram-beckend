package com.stugram.app.ui.home

import android.net.Uri

sealed interface DirectChatAction {
    data object InitialLoad : DirectChatAction
    data object LoadMore : DirectChatAction
    data class ComposerChanged(val value: String) : DirectChatAction
    data class SendText(val text: String, val replyToId: String?) : DirectChatAction
    data class SendMedia(val uri: Uri, val mimeType: String, val replyToId: String?) : DirectChatAction
    data class Retry(val localId: String) : DirectChatAction
    data object MarkVisibleMessagesSeen : DirectChatAction
    data object Reconnect : DirectChatAction
}

sealed interface GroupChatAction {
    data object InitialLoad : GroupChatAction
    data object LoadMore : GroupChatAction
    data class ComposerChanged(val value: String) : GroupChatAction
    data class SendText(val text: String, val replyToId: String?) : GroupChatAction
    data class SendMedia(val uri: Uri, val mimeType: String, val replyToId: String?) : GroupChatAction
    data class Retry(val localId: String) : GroupChatAction
    data object MarkVisibleMessagesSeen : GroupChatAction
    data object Reconnect : GroupChatAction
}
