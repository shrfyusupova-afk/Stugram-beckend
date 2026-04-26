package com.stugram.app.data.local.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.stugram.app.core.observability.ChatReliabilityEvent
import com.stugram.app.core.observability.ChatReliabilityLogger
import com.stugram.app.core.observability.ChatReliabilityMetrics
import com.stugram.app.core.observability.ChatReliabilitySink
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.MessageReactionModel
import com.stugram.app.data.remote.model.ProfileSummary
import com.stugram.app.data.repository.PendingChatPayload
import com.stugram.app.domain.chat.ChatDomainEvent
import com.stugram.app.domain.chat.ChatMessageSnapshot
import com.stugram.app.domain.chat.ChatMessageStatus
import com.stugram.app.domain.chat.MessageSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatRoomStoreTest {
    private lateinit var database: ChatDatabase
    private lateinit var store: ChatRoomStore
    private val reliabilityEvents = mutableListOf<ChatReliabilityEvent>()

    @Before
    fun setUp() {
        ChatReliabilityMetrics.resetForTest()
        reliabilityEvents.clear()
        ChatReliabilityLogger.sink = object : ChatReliabilitySink {
            override fun emit(entry: ChatReliabilityEvent) {
                reliabilityEvents += entry
            }
        }
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java
        ).allowMainThreadQueries().build()
        store = ChatRoomStore.fromDatabase(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun eventAppliedTwice_hasSameResult() = runBlocking {
        val message = testMessage(id = "m1", clientId = "c1", text = "hello")
        store.applyRemoteMessage("direct", "conv-1", message, MessageSource.HttpResponse, serverSequence = 10)
        store.applyRemoteMessage("direct", "conv-1", message, MessageSource.HttpResponse, serverSequence = 10)

        val result = store.loadMessageModels("direct", "conv-1")
        assertEquals(1, result.size)
        assertEquals("m1", result.first().id)
    }

    @Test
    fun deletePreventsResurrectionAfterRestart() = runBlocking {
        val message = testMessage(id = "m1", clientId = "c1", text = "hello")
        store.applyRemoteMessage("direct", "conv-1", message, MessageSource.HttpResponse, serverSequence = 10)
        store.applyDelete("direct", "conv-1", "m1", deleteSequence = 20)

        val restartedStore = ChatRoomStore.fromDatabase(database)
        restartedStore.applyRemoteMessage("direct", "conv-1", message, MessageSource.PaginationLoad, serverSequence = 5)

        val result = restartedStore.loadMessageModels("direct", "conv-1")
        assertTrue(result.isEmpty())
        assertNotNull(database.tombstoneDao().get("direct", "conv-1", "m1"))
    }

    @Test
    fun retryDoesNotDuplicateConfirmedMessage() = runBlocking {
        store.upsertPending("direct", "conv-1", "c1", PendingChatPayload(text = "hello"))
        store.applyRemoteMessage("direct", "conv-1", testMessage(id = "m1", clientId = "c1", text = "hello"), MessageSource.SocketEvent, serverSequence = 10)

        assertTrue(store.hasConfirmedMessage("direct", "conv-1", "c1"))
        store.removePending("c1")
        val pending = store.loadPending("direct", "conv-1")
        val messages = store.loadMessageModels("direct", "conv-1")
        assertTrue(pending.isEmpty())
        assertEquals(1, messages.size)
    }

    @Test
    fun socketAndHttpRaceStoredCorrectly() = runBlocking {
        val message = testMessage(id = "m1", clientId = "c1", text = "hello")
        store.applyRemoteMessage("direct", "conv-1", message, MessageSource.SocketEvent, serverSequence = 10)
        store.applyRemoteMessage("direct", "conv-1", message, MessageSource.HttpResponse, serverSequence = 10)

        val result = store.loadMessageModels("direct", "conv-1")
        assertEquals(1, result.size)
        assertEquals("c1", result.first().clientId)
    }

    @Test
    fun cursorUpdatedCorrectly() = runBlocking {
        store.applyRemoteMessage("direct", "conv-1", testMessage(id = "m1", clientId = "c1"), MessageSource.SocketEvent, serverSequence = 10)
        store.applyRemoteMessage("direct", "conv-1", testMessage(id = "m2", clientId = "c2"), MessageSource.SocketEvent, serverSequence = 8)

        assertEquals(10L, store.currentCursor("direct", "conv-1"))
    }

    @Test
    fun pendingRemovedAfterSuccess() = runBlocking {
        store.upsertPending("direct", "conv-1", "c1", PendingChatPayload(text = "hello"))
        store.applyRemoteMessage("direct", "conv-1", testMessage(id = "m1", clientId = "c1", text = "hello"), MessageSource.HttpResponse, serverSequence = 10)

        val pending = store.loadPending("direct", "conv-1")
        assertTrue(pending.isEmpty())
    }

    @Test
    fun confirmPendingSuccessWritesMessageBeforeRemovingPending() = runBlocking {
        store.upsertPending("direct", "conv-1", "c1", PendingChatPayload(text = "hello"))

        store.confirmPendingSuccess(
            scope = "direct",
            targetId = "conv-1",
            localId = "c1",
            model = testMessage(id = "m1", clientId = "c1", text = "hello")
        )

        val pending = store.loadPending("direct", "conv-1")
        val messages = store.loadMessageModels("direct", "conv-1")
        assertTrue(pending.isEmpty())
        assertEquals(1, messages.size)
        assertEquals("m1", messages.first().id)
        assertEquals("c1", messages.first().clientId)
    }

    @Test
    fun pendingRetryPersistsNextAttemptAndErrorCode() = runBlocking {
        store.upsertPending("direct", "conv-1", "c1", PendingChatPayload(text = "hello"))
        val nextAttemptAt = 1_900_000_000_000L

        store.markPendingRetry("c1", nextAttemptAt, "RATE_LIMITED")

        val pending = store.loadPending("direct", "conv-1").single()
        assertEquals(1, pending.retryCount)
        assertEquals(nextAttemptAt, pending.nextAttemptAt)
        assertEquals("RATE_LIMITED", pending.errorCode)
    }

    @Test
    fun pendingMediaStoresStagedPathAndMetadata() = runBlocking {
        store.upsertPending(
            "direct",
            "conv-1",
            "c1",
            PendingChatPayload(
                mediaUri = "content://external/image/1",
                mediaLocalPath = "/private/files/chat_media/c1/original",
                mimeType = "image/jpeg",
                fileSize = 1234L,
                originalDisplayName = "photo.jpg"
            )
        )

        val pending = store.loadPending("direct", "conv-1").single()
        assertEquals("/private/files/chat_media/c1/original", pending.payload.mediaLocalPath)
        assertEquals("image/jpeg", pending.payload.mimeType)
        assertEquals(1234L, pending.payload.fileSize)
        assertEquals("photo.jpg", pending.payload.originalDisplayName)
    }

    @Test
    fun terminalFailureRemovesPendingAndKeepsFailedMessageRow() = runBlocking {
        store.upsertPending("direct", "conv-1", "c1", PendingChatPayload(text = "hello"))

        store.markTerminalFailureAndRemove(
            localId = "c1",
            errorCode = "BLOCKED_USER",
            message = "You cannot send this message because this conversation is blocked."
        )

        val pending = store.loadPending("direct", "conv-1")
        val entities = database.messageDao().getMessages("direct", "conv-1")
        assertTrue(pending.isEmpty())
        assertEquals(1, entities.size)
        assertEquals(ChatMessageStatus.FAILED.name, entities.first().status)
        assertEquals("c1", entities.first().clientId)
    }

    @Test
    fun stalePaginationIgnored() = runBlocking {
        store.applyRemoteMessage("direct", "conv-1", testMessage(id = "m1", clientId = "c1", text = "new"), MessageSource.SocketEvent, serverSequence = 20)
        store.applyEvent(
            "direct",
            "conv-1",
            ChatDomainEvent.ServerMessageEdited(
                messageId = "m1",
                text = "edited",
                editVersion = 2,
                serverSequence = 21
            )
        )
        store.applyRemoteMessage("direct", "conv-1", testMessage(id = "m1", clientId = "c1", text = "old"), MessageSource.PaginationLoad, serverSequence = 5)

        val result = store.loadMessageModels("direct", "conv-1")
        assertEquals(1, result.size)
        assertEquals("edited", result.first().text)
    }

    @Test
    fun largeConversationLoadsRecentWindowOnly() = runBlocking {
        repeat(1_000) { index ->
            store.applyRemoteMessage(
                "direct",
                "conv-1",
                testMessage(id = "m$index", clientId = "c$index", text = "message-$index"),
                MessageSource.HttpResponse,
                serverSequence = index.toLong() + 1
            )
        }

        val recent = store.loadMessageModels("direct", "conv-1", limit = 80)
        assertEquals(80, recent.size)
        assertEquals("m920", recent.first().id)
        assertEquals("m999", recent.last().id)
    }

    @Test
    fun queueHealthMetricsUpdateFromPendingState() = runBlocking {
        store.upsertPending("direct", "conv-1", "c1", PendingChatPayload(text = "hello"))

        val snapshotAfterInsert = ChatReliabilityMetrics.snapshot()
        assertEquals(1L, snapshotAfterInsert["gauge:chat_pending_queue_depth"])
        assertTrue((snapshotAfterInsert["gauge:chat_pending_oldest_age_ms"] ?: 0L) >= 0L)

        store.removePending("c1")

        val snapshotAfterRemove = ChatReliabilityMetrics.snapshot()
        assertEquals(0L, snapshotAfterRemove["gauge:chat_pending_queue_depth"])
    }

    @Test
    fun applyEventEmitsStructuredStoreLogsWithoutMessageText() = runBlocking {
        store.applyEvent(
            "direct",
            "conv-1",
            ChatDomainEvent.ServerMessageCreated(
                message = ChatMessageSnapshot(
                    clientId = "c1",
                    backendId = "m1",
                    text = "hello hidden text",
                    status = ChatMessageStatus.SENT,
                    source = MessageSource.HttpResponse,
                    timestamp = 10L
                ),
                serverSequence = 10L
            )
        )

        val started = reliabilityEvents.firstOrNull { it.event == "chat_store_apply_event_started" }
        val succeeded = reliabilityEvents.firstOrNull { it.event == "chat_store_apply_event_succeeded" }
        assertNotNull(started)
        assertNotNull(succeeded)
        assertEquals("direct", started?.fields?.get("targetType"))
        assertEquals("conv-1", started?.fields?.get("targetId"))
        assertFalse(started?.fields?.values?.contains("hello hidden text") == true)
    }

    @Test
    fun singleEventUpdatesOnlyMatchingRowWithoutDuplicates() = runBlocking {
        repeat(200) { index ->
            store.applyRemoteMessage(
                "direct",
                "conv-1",
                testMessage(id = "m$index", clientId = "c$index", text = "message-$index"),
                MessageSource.HttpResponse,
                serverSequence = index.toLong() + 1
            )
        }

        store.applyEvent(
            "direct",
            "conv-1",
            ChatDomainEvent.ServerMessageEdited(
                messageId = "m150",
                text = "edited-150",
                editVersion = 201,
                serverSequence = 201
            )
        )

        val entities = database.messageDao().getMessages("direct", "conv-1")
        assertEquals(200, entities.size)
        assertEquals(1, entities.count { it.backendId == "m150" })
        assertEquals("edited-150", entities.first { it.backendId == "m150" }.text)
        assertEquals("message-149", entities.first { it.backendId == "m149" }.text)
    }

    @Test
    fun deletePreventsResurrectionFromPendingSnapshot() = runBlocking {
        store.applyEvent(
            "group",
            "group-1",
            ChatDomainEvent.ServerMessageCreated(
                message = ChatMessageSnapshot(
                    clientId = "c1",
                    backendId = "m1",
                    rawJson = Gson().toJson(testMessage(id = "m1", clientId = "c1", text = "hello")),
                    text = "hello",
                    status = ChatMessageStatus.SENT,
                    source = MessageSource.SocketEvent,
                    timestamp = 10,
                    serverSequence = 10
                ),
                serverSequence = 10
            )
        )
        store.applyDelete("group", "group-1", "m1", 11)
        store.applyRemoteMessage("group", "group-1", testMessage(id = "m1", clientId = "c1", text = "hello"), MessageSource.HttpResponse, serverSequence = 10)

        assertFalse(store.loadMessageModels("group", "group-1").any { it.id == "m1" })
    }

    @Test
    fun processDeathPendingAndConfirmedStateRemainDeterministicAfterRestart() = runBlocking {
        store.upsertPending("direct", "conv-1", "c1", PendingChatPayload(text = "hello"))
        val restartedStore = ChatRoomStore.fromDatabase(database)

        val pendingAfterRestart = restartedStore.loadPending("direct", "conv-1")
        assertEquals(1, pendingAfterRestart.size)
        assertEquals("c1", pendingAfterRestart.single().localId)

        restartedStore.confirmPendingSuccess(
            scope = "direct",
            targetId = "conv-1",
            localId = "c1",
            model = testMessage(id = "m1", clientId = "c1", text = "hello")
        )

        val secondRestart = ChatRoomStore.fromDatabase(database)
        assertTrue(secondRestart.loadPending("direct", "conv-1").isEmpty())
        assertEquals(1, secondRestart.loadMessageModels("direct", "conv-1").size)
        assertEquals("m1", secondRestart.loadMessageModels("direct", "conv-1").single().id)
    }

    @Test
    fun duplicateSocketAndReplayApplyRemainIdempotent() = runBlocking {
        val message = testMessage(id = "m1", clientId = "c1", text = "hello")

        store.applyEvent(
            "direct",
            "conv-1",
            ChatDomainEvent.ServerMessageCreated(
                message = message.toSnapshotForTest(MessageSource.SocketEvent),
                serverSequence = 10L
            )
        )
        store.applyEvent(
            "direct",
            "conv-1",
            ChatDomainEvent.ServerMessageCreated(
                message = message.toSnapshotForTest(MessageSource.ReconnectReconciliation),
                serverSequence = 10L
            )
        )

        val messages = store.loadMessageModels("direct", "conv-1")
        assertEquals(1, messages.size)
        assertEquals("m1", messages.single().id)
    }

    @Test
    fun replaySequenceAppliesInOrderAndCursorAdvancesWithoutSkipping() = runBlocking {
        store.applyEvent(
            "group",
            "group-1",
            ChatDomainEvent.ServerMessageCreated(
                message = testMessage(id = "m1", clientId = "c1", text = "hello").toSnapshotForTest(MessageSource.ReconnectReconciliation),
                serverSequence = 11L
            )
        )
        store.applyEvent(
            "group",
            "group-1",
            ChatDomainEvent.ServerMessageEdited(
                messageId = "m1",
                text = "edited",
                editVersion = 12,
                serverSequence = 12L
            )
        )
        store.applyEvent(
            "group",
            "group-1",
            ChatDomainEvent.ServerReactionSnapshot(
                messageId = "m1",
                reactionsJson = "[\"u2:🔥\"]",
                reactionVersion = 13,
                serverSequence = 13L
            )
        )

        val message = database.messageDao().getMessages("group", "group-1").single()
        assertEquals("edited", message.text)
        assertEquals(13L, store.currentCursor("group", "group-1"))
        assertEquals("[\"u2:🔥\"]", message.reactionsJson)
    }

    private fun testMessage(
        id: String,
        clientId: String,
        text: String = "text"
    ): ChatMessageModel = ChatMessageModel(
        id = id,
        clientId = clientId,
        text = text,
        sender = ProfileSummary(
            id = "user-1",
            username = "user1",
            fullName = "User One"
        ),
        reactions = listOf(MessageReactionModel(emoji = "🔥")),
        createdAt = "2026-04-22T10:00:00Z",
        updatedAt = "2026-04-22T10:00:00Z"
    )

    private fun ChatMessageModel.toSnapshotForTest(source: MessageSource): ChatMessageSnapshot =
        ChatMessageSnapshot(
            localId = clientId,
            clientId = clientId,
            backendId = id,
            rawJson = Gson().toJson(this),
            text = text.orEmpty(),
            status = ChatMessageStatus.SENT,
            source = source,
            timestamp = 1L,
            serverSequence = serverSequence ?: 1L,
            hasReactionSnapshot = true
        )
}
