package com.stugram.app.ui.home

import com.stugram.app.core.messaging.ForwardDraft
import com.stugram.app.core.socket.UserPresenceState
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.GroupConversationModel
import com.stugram.app.data.remote.model.GroupMemberModel
import com.stugram.app.data.remote.model.ProfileModel

data class DirectChatUiState(
    val targetId: String,
    val messages: List<MessageData> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: ChatError? = null,
    val connectionState: ChatConnectionState = ChatConnectionState.Unknown,
    val pendingCount: Int = 0,
    val composerText: String = "",
    val typingState: ChatTypingState = ChatTypingState.Idle,
    val canSend: Boolean = true,
    val sendDisabledReason: String? = null,
    val currentUser: ProfileModel? = null,
    val remoteProfile: ProfileModel? = null,
    val remotePresence: UserPresenceState? = null,
    val conversationDetail: DirectConversationModel? = null,
    val isMuted: Boolean = false,
    val searchResults: List<MessageData> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val forwardDraft: ForwardDraft? = null
)

data class GroupChatUiState(
    val groupId: String,
    val messages: List<GroupMessageData> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: ChatError? = null,
    val connectionState: ChatConnectionState = ChatConnectionState.Unknown,
    val pendingCount: Int = 0,
    val composerText: String = "",
    val typingUsers: List<String> = emptyList(),
    val canSend: Boolean = true,
    val sendDisabledReason: String? = null,
    val groupTitle: String = "",
    val memberCount: Int = 0,
    val currentUser: ProfileModel? = null,
    val groupDetail: GroupConversationModel? = null,
    val groupMembers: List<GroupMemberModel> = emptyList(),
    val onlineMembersCount: Int = 0,
    val searchResults: List<GroupMessageData> = emptyList(),
    val searchLoading: Boolean = false,
    val forwardDraft: ForwardDraft? = null
)
