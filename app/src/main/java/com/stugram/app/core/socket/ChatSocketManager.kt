package com.stugram.app.core.socket

import android.util.Log
import com.stugram.app.BuildConfig
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.data.remote.model.DirectConversationModel
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.GroupConversationModel
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatSocketManager(private val tokenManager: TokenManager) {
    private var socket: Socket? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _newMessages = MutableSharedFlow<ChatMessageModel>(extraBufferCapacity = 64)
    val newMessages: SharedFlow<ChatMessageModel> = _newMessages

    private val _groupMessages = MutableSharedFlow<Pair<String, ChatMessageModel>>(extraBufferCapacity = 64)
    val groupMessages: SharedFlow<Pair<String, ChatMessageModel>> = _groupMessages

    private val _typingEvents = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 16)
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents

    private val _presenceEvents = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 16)
    val presenceEvents: SharedFlow<PresenceEvent> = _presenceEvents
    private val _connectionEvents = MutableSharedFlow<SocketConnectionEvent>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<SocketConnectionEvent> = _connectionEvents
    private val _conversationUpdates = MutableSharedFlow<DirectConversationModel>(extraBufferCapacity = 32)
    val conversationUpdates: SharedFlow<DirectConversationModel> = _conversationUpdates
    private val _messageSeenEvents = MutableSharedFlow<MessageSeenEvent>(extraBufferCapacity = 32)
    val messageSeenEvents: SharedFlow<MessageSeenEvent> = _messageSeenEvents
    private val _messageReactionEvents = MutableSharedFlow<MessageReactionEvent>(extraBufferCapacity = 32)
    val messageReactionEvents: SharedFlow<MessageReactionEvent> = _messageReactionEvents
    private val _messageEditedEvents = MutableSharedFlow<MessageEditedEvent>(extraBufferCapacity = 32)
    val messageEditedEvents: SharedFlow<MessageEditedEvent> = _messageEditedEvents
    private val _messageForwardedEvents = MutableSharedFlow<MessageForwardedEvent>(extraBufferCapacity = 32)
    val messageForwardedEvents: SharedFlow<MessageForwardedEvent> = _messageForwardedEvents
    private val _messagePinnedEvents = MutableSharedFlow<MessagePinnedEvent>(extraBufferCapacity = 16)
    val messagePinnedEvents: SharedFlow<MessagePinnedEvent> = _messagePinnedEvents
    private val _messageUnpinnedEvents = MutableSharedFlow<MessagePinnedEvent>(extraBufferCapacity = 16)
    val messageUnpinnedEvents: SharedFlow<MessagePinnedEvent> = _messageUnpinnedEvents
    private val _messageDeletedForEveryoneEvents = MutableSharedFlow<MessageDeletedForEveryoneEvent>(extraBufferCapacity = 16)
    val messageDeletedForEveryoneEvents: SharedFlow<MessageDeletedForEveryoneEvent> = _messageDeletedForEveryoneEvents
    private val _messageDeletedEvents = MutableSharedFlow<MessageDeletedEvent>(extraBufferCapacity = 16)
    val messageDeletedEvents: SharedFlow<MessageDeletedEvent> = _messageDeletedEvents
    private val _messageDeliveredEvents = MutableSharedFlow<MessageDeliveredEvent>(extraBufferCapacity = 16)
    val messageDeliveredEvents: SharedFlow<MessageDeliveredEvent> = _messageDeliveredEvents
    private val _groupConversationUpdates = MutableSharedFlow<GroupConversationModel>(extraBufferCapacity = 32)
    val groupConversationUpdates: SharedFlow<GroupConversationModel> = _groupConversationUpdates
    private val _groupMessageSeenEvents = MutableSharedFlow<GroupMessageSeenEvent>(extraBufferCapacity = 32)
    val groupMessageSeenEvents: SharedFlow<GroupMessageSeenEvent> = _groupMessageSeenEvents
    private val _groupMessageReactionEvents = MutableSharedFlow<GroupMessageReactionEvent>(extraBufferCapacity = 32)
    val groupMessageReactionEvents: SharedFlow<GroupMessageReactionEvent> = _groupMessageReactionEvents
    private val _groupMessageEditedEvents = MutableSharedFlow<GroupMessageEditedEvent>(extraBufferCapacity = 32)
    val groupMessageEditedEvents: SharedFlow<GroupMessageEditedEvent> = _groupMessageEditedEvents
    private val _groupMessageForwardedEvents = MutableSharedFlow<GroupMessageForwardedEvent>(extraBufferCapacity = 32)
    val groupMessageForwardedEvents: SharedFlow<GroupMessageForwardedEvent> = _groupMessageForwardedEvents
    private val _groupMessagePinnedEvents = MutableSharedFlow<GroupMessagePinnedEvent>(extraBufferCapacity = 16)
    val groupMessagePinnedEvents: SharedFlow<GroupMessagePinnedEvent> = _groupMessagePinnedEvents
    private val _groupMessageUnpinnedEvents = MutableSharedFlow<GroupMessagePinnedEvent>(extraBufferCapacity = 16)
    val groupMessageUnpinnedEvents: SharedFlow<GroupMessagePinnedEvent> = _groupMessageUnpinnedEvents
    private val _groupMessageDeletedForEveryoneEvents = MutableSharedFlow<GroupMessageDeletedForEveryoneEvent>(extraBufferCapacity = 16)
    val groupMessageDeletedForEveryoneEvents: SharedFlow<GroupMessageDeletedForEveryoneEvent> = _groupMessageDeletedForEveryoneEvents
    private val _groupMessageDeletedEvents = MutableSharedFlow<GroupMessageDeletedEvent>(extraBufferCapacity = 16)
    val groupMessageDeletedEvents: SharedFlow<GroupMessageDeletedEvent> = _groupMessageDeletedEvents
    private val _groupMemberAddedEvents = MutableSharedFlow<GroupMemberEvent>(extraBufferCapacity = 16)
    val groupMemberAddedEvents: SharedFlow<GroupMemberEvent> = _groupMemberAddedEvents
    private val _groupMemberRemovedEvents = MutableSharedFlow<GroupMemberEvent>(extraBufferCapacity = 16)
    val groupMemberRemovedEvents: SharedFlow<GroupMemberEvent> = _groupMemberRemovedEvents
    private val joinedConversationIds = linkedSetOf<String>()
    private var hasConnectedBefore = false

    fun connect() {
        if (socket?.connected() == true) return

        scope.launch {
            val token = tokenManager.getAccessToken() ?: return@launch
            val opts = IO.Options().apply {
                extraHeaders = mapOf("Authorization" to listOf("Bearer $token"))
                forceNew = true
                reconnection = true
            }

            try {
                // Remove /api/v1/ from base URL for socket connection if present
                val baseUrl = BuildConfig.API_BASE_URL.replace("/api/v1/", "")
                socket = IO.socket(baseUrl, opts)

                setupListeners()
                socket?.connect()
                Log.d("ChatSocketManager", "Connecting to $baseUrl")
            } catch (e: Exception) {
                Log.e("ChatSocketManager", "Socket connection error", e)
            }
        }
    }

    private fun setupListeners() {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d("ChatSocketManager", "Socket Connected")
            val event = if (hasConnectedBefore) SocketConnectionEvent.Reconnected else SocketConnectionEvent.Connected
            hasConnectedBefore = true
            scope.launch { _connectionEvents.emit(event) }
            joinedConversationIds.forEach { id ->
                val data = JSONObject().put("conversationId", id)
                socket?.emit("conversation:join", data)
            }
        }

        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.d("ChatSocketManager", "Socket Disconnected")
            scope.launch { _connectionEvents.emit(SocketConnectionEvent.Disconnected) }
        }

        socket?.on("new_message") { args ->
            val data = args[0] as JSONObject
            val message = gson.fromJson(data.toString(), ChatMessageModel::class.java)
            scope.launch { _newMessages.emit(message) }
        }

        socket?.on("conversation_updated") { args ->
            val data = args[0] as JSONObject
            val conversation = gson.fromJson(data.toString(), DirectConversationModel::class.java)
            scope.launch { _conversationUpdates.emit(conversation) }
        }

        socket?.on("message_seen") { args ->
            val data = args[0] as JSONObject
            val payload = MessageSeenEvent(
                conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java),
                seenByUserId = data.optString("seenByUserId").takeIf { it.isNotBlank() },
                seenAt = data.optString("seenAt").takeIf { it.isNotBlank() },
                readAt = data.optString("readAt").takeIf { it.isNotBlank() }
            )
            scope.launch { _messageSeenEvents.emit(payload) }
        }

        socket?.on("message_reaction_updated") { args ->
            val data = args[0] as JSONObject
            val payload = MessageReactionEvent(
                conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
            )
            scope.launch { _messageReactionEvents.emit(payload) }
        }

        socket?.on("message_edited") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _messageEditedEvents.emit(
                    MessageEditedEvent(
                        conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
                    )
                )
            }
        }

        socket?.on("message_forwarded") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _messageForwardedEvents.emit(
                    MessageForwardedEvent(
                        conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
                    )
                )
            }
        }

        socket?.on("message_pinned") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _messagePinnedEvents.emit(
                    MessagePinnedEvent(
                        conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                        conversation = data.optJSONObject("conversation")?.let {
                            gson.fromJson(it.toString(), DirectConversationModel::class.java)
                        }
                    )
                )
            }
        }

        socket?.on("message_unpinned") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _messageUnpinnedEvents.emit(
                    MessagePinnedEvent(
                        conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                        conversation = data.optJSONObject("conversation")?.let {
                            gson.fromJson(it.toString(), DirectConversationModel::class.java)
                        }
                    )
                )
            }
        }

        socket?.on("message_deleted_for_everyone") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _messageDeletedForEveryoneEvents.emit(
                    MessageDeletedForEveryoneEvent(
                        conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
                    )
                )
            }
        }

        socket?.on("message_deleted") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _messageDeletedEvents.emit(
                    MessageDeletedEvent(
                        conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                        messageId = data.optString("messageId"),
                        deletedByUserId = data.optString("deletedByUserId").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        socket?.on("message_delivered") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _messageDeliveredEvents.emit(
                    MessageDeliveredEvent(
                        conversationId = data.optString("conversationId").takeIf { it.isNotBlank() },
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        messageId = data.optString("messageId").takeIf { it.isNotBlank() },
                        deliveredAt = data.optString("deliveredAt").takeIf { it.isNotBlank() },
                        recipientIds = buildList {
                            val array = data.optJSONArray("recipientIds")
                            if (array != null) {
                                for (index in 0 until array.length()) {
                                    val item = array.optString(index)
                                    if (item.isNotBlank()) add(item)
                                }
                            }
                        }
                    )
                )
            }
        }

        socket?.on("group_message") { args ->
            val data = args[0] as JSONObject
            val groupId = data.optString("groupId")
            val messageData = data.optJSONObject("message")
            if (messageData != null) {
                val message = gson.fromJson(messageData.toString(), ChatMessageModel::class.java)
                scope.launch { _groupMessages.emit(groupId to message) }
            }
        }

        socket?.on("group_conversation_updated") { args ->
            val data = args[0] as JSONObject
            val group = gson.fromJson(data.toString(), GroupConversationModel::class.java)
            scope.launch { _groupConversationUpdates.emit(group) }
        }

        socket?.on("group_message_seen") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessageSeenEvents.emit(
                    GroupMessageSeenEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java),
                        seenByUserId = data.optString("seenByUserId").takeIf { it.isNotBlank() },
                        seenAt = data.optString("seenAt").takeIf { it.isNotBlank() },
                        readAt = data.optString("readAt").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        socket?.on("group_message_reaction_updated") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessageReactionEvents.emit(
                    GroupMessageReactionEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
                    )
                )
            }
        }

        socket?.on("group_message_edited") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessageEditedEvents.emit(
                    GroupMessageEditedEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
                    )
                )
            }
        }

        socket?.on("group_message_forwarded") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessageForwardedEvents.emit(
                    GroupMessageForwardedEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
                    )
                )
            }
        }

        socket?.on("group_message_pinned") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessagePinnedEvents.emit(
                    GroupMessagePinnedEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        group = data.optJSONObject("group")?.let {
                            gson.fromJson(it.toString(), GroupConversationModel::class.java)
                        }
                    )
                )
            }
        }

        socket?.on("group_message_unpinned") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessageUnpinnedEvents.emit(
                    GroupMessagePinnedEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        group = data.optJSONObject("group")?.let {
                            gson.fromJson(it.toString(), GroupConversationModel::class.java)
                        }
                    )
                )
            }
        }

        socket?.on("group_message_deleted_for_everyone") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessageDeletedForEveryoneEvents.emit(
                    GroupMessageDeletedForEveryoneEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        message = gson.fromJson(data.getJSONObject("message").toString(), ChatMessageModel::class.java)
                    )
                )
            }
        }

        socket?.on("group_message_deleted") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMessageDeletedEvents.emit(
                    GroupMessageDeletedEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        messageId = data.optString("messageId"),
                        deletedByUserId = data.optString("deletedByUserId").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        socket?.on("group_member_added") { args ->
            val data = args[0] as JSONObject
            val group = data.optJSONObject("group")?.let { gson.fromJson(it.toString(), GroupConversationModel::class.java) }
            scope.launch {
                _groupMemberAddedEvents.emit(
                    GroupMemberEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        group = group
                    )
                )
            }
        }

        socket?.on("group_member_removed") { args ->
            val data = args[0] as JSONObject
            scope.launch {
                _groupMemberRemovedEvents.emit(
                    GroupMemberEvent(
                        groupId = data.optString("groupId").takeIf { it.isNotBlank() },
                        userId = data.optString("userId").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        socket?.on("typing_start") { args ->
            val data = args[0] as JSONObject
            val event = TypingEvent(
                conversationId = data.optString("conversationId").takeIf { it.isNotEmpty() },
                groupId = data.optString("groupId").takeIf { it.isNotEmpty() },
                userId = data.optJSONObject("user")?.optString("_id") ?: "",
                username = data.optJSONObject("user")?.optString("username") ?: "",
                isTyping = true
            )
            scope.launch { _typingEvents.emit(event) }
        }

        socket?.on("typing_stop") { args ->
            val data = args[0] as JSONObject
            val event = TypingEvent(
                conversationId = data.optString("conversationId").takeIf { it.isNotEmpty() },
                groupId = data.optString("groupId").takeIf { it.isNotEmpty() },
                userId = data.optString("userId"),
                username = "",
                isTyping = false
            )
            scope.launch { _typingEvents.emit(event) }
        }

        socket?.on("user_online") { args ->
            val data = args[0] as JSONObject
            val userId = data.optString("userId")
            scope.launch { _presenceEvents.emit(PresenceEvent(userId, true)) }
        }

        socket?.on("user_offline") { args ->
            val data = args[0] as JSONObject
            val userId = data.optString("userId")
            scope.launch { _presenceEvents.emit(PresenceEvent(userId, false)) }
        }
    }

    fun joinConversation(conversationId: String) {
        joinedConversationIds.add(conversationId)
        val data = JSONObject().put("conversationId", conversationId)
        socket?.emit("conversation:join", data)
    }

    fun joinGroup(groupId: String) {
        val data = JSONObject().put("groupId", groupId)
        socket?.emit("group_chat:join", data)
    }

    fun sendTypingStart(conversationId: String? = null, groupId: String? = null) {
        val data = JSONObject()
        conversationId?.let { data.put("conversationId", it) }
        groupId?.let { data.put("groupId", it) }
        socket?.emit("typing_start", data)
    }

    fun sendTypingStop(conversationId: String? = null, groupId: String? = null) {
        val data = JSONObject()
        conversationId?.let { data.put("conversationId", it) }
        groupId?.let { data.put("groupId", it) }
        socket?.emit("typing_stop", data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        hasConnectedBefore = false
        joinedConversationIds.clear()
    }

    companion object {
        @Volatile
        private var INSTANCE: ChatSocketManager? = null

        fun getInstance(tokenManager: TokenManager): ChatSocketManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatSocketManager(tokenManager).also { INSTANCE = it }
            }
        }
    }
}

data class TypingEvent(
    val conversationId: String?,
    val groupId: String?,
    val userId: String,
    val username: String,
    val isTyping: Boolean
)

data class PresenceEvent(
    val userId: String,
    val isOnline: Boolean
)

data class ConversationUpdateEvent(
    val conversation: DirectConversationModel
)

data class MessageSeenEvent(
    val conversationId: String?,
    val message: ChatMessageModel,
    val seenByUserId: String?,
    val seenAt: String?,
    val readAt: String?
)

data class MessageReactionEvent(
    val conversationId: String?,
    val message: ChatMessageModel
)

data class MessageEditedEvent(
    val conversationId: String?,
    val message: ChatMessageModel
)

data class MessageForwardedEvent(
    val conversationId: String?,
    val message: ChatMessageModel
)

data class MessagePinnedEvent(
    val conversationId: String?,
    val conversation: DirectConversationModel?
)

data class MessageDeletedForEveryoneEvent(
    val conversationId: String?,
    val message: ChatMessageModel
)

data class MessageDeletedEvent(
    val conversationId: String?,
    val messageId: String,
    val deletedByUserId: String?
)

data class MessageDeliveredEvent(
    val conversationId: String?,
    val groupId: String?,
    val messageId: String?,
    val deliveredAt: String?,
    val recipientIds: List<String>
)

data class GroupMessageSeenEvent(
    val groupId: String?,
    val message: ChatMessageModel,
    val seenByUserId: String?,
    val seenAt: String?,
    val readAt: String?
)

data class GroupMessageReactionEvent(
    val groupId: String?,
    val message: ChatMessageModel
)

data class GroupMessageEditedEvent(
    val groupId: String?,
    val message: ChatMessageModel
)

data class GroupMessageForwardedEvent(
    val groupId: String?,
    val message: ChatMessageModel
)

data class GroupMessagePinnedEvent(
    val groupId: String?,
    val group: GroupConversationModel?
)

data class GroupMessageDeletedForEveryoneEvent(
    val groupId: String?,
    val message: ChatMessageModel
)

data class GroupMessageDeletedEvent(
    val groupId: String?,
    val messageId: String,
    val deletedByUserId: String?
)

data class GroupMemberEvent(
    val groupId: String?,
    val group: GroupConversationModel? = null,
    val userId: String? = null
)

sealed class SocketConnectionEvent {
    data object Connected : SocketConnectionEvent()
    data object Reconnected : SocketConnectionEvent()
    data object Disconnected : SocketConnectionEvent()
}
