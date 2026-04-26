package com.stugram.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {
    @Test
    fun optimisticAndHttpSuccessMergeIntoOneRow() {
        val optimistic = msg(localId = "local-1", clientId = "local-1", status = ChatMessageStatus.QUEUED, source = MessageSource.LocalOptimistic)
        val server = msg(localId = "local-1", clientId = "local-1", backendId = "m1", status = ChatMessageStatus.SENT, source = MessageSource.HttpResponse, serverSequence = 10)

        val result = ChatReducer.reduce(listOf(optimistic), server)

        assertEquals(1, result.size)
        assertEquals("m1", result.single().backendId)
        assertEquals(ChatMessageStatus.SENT, result.single().status)
    }

    @Test
    fun optimisticAndSocketSuccessMergeIntoOneRow() {
        val optimistic = msg(localId = "local-1", clientId = "local-1", status = ChatMessageStatus.SENDING, source = MessageSource.LocalOptimistic)
        val result = ChatReducer.reduce(
            listOf(optimistic),
            ChatDomainEvent.ServerMessageCreated(
                message = msg(localId = "local-1", clientId = "local-1", backendId = "m1", status = ChatMessageStatus.SENT, source = MessageSource.SocketEvent),
                serverSequence = 11
            )
        )

        assertEquals(1, result.size)
        assertEquals("m1", result.single().backendId)
    }

    @Test
    fun httpSuccessAndSocketSuccessDoNotCreateDuplicates() {
        val http = msg(clientId = "c1", backendId = "m1", status = ChatMessageStatus.SENT, source = MessageSource.HttpResponse, serverSequence = 12)
        val result = ChatReducer.reduce(
            listOf(http),
            ChatDomainEvent.ServerMessageCreated(
                message = msg(clientId = "c1", backendId = "m1", status = ChatMessageStatus.SENT, source = MessageSource.SocketEvent, serverSequence = 12),
                serverSequence = 12
            )
        )

        assertEquals(1, result.size)
        assertEquals(12L, result.single().serverSequence)
    }

    @Test
    fun failedRetryKeepsSameRowIdentity() {
        val failed = msg(localId = "local-1", clientId = "local-1", status = ChatMessageStatus.FAILED, source = MessageSource.LocalRetry)
        val result = ChatReducer.reduce(listOf(failed), ChatDomainEvent.LocalSending("local-1"))

        assertEquals(1, result.size)
        assertEquals("local-1", result.single().clientId)
        assertEquals(ChatMessageStatus.SENDING, result.single().status)
    }

    @Test
    fun queuedSendingSentWorks() {
        val queued = ChatReducer.reduce(emptyList(), ChatDomainEvent.LocalQueued(msg(clientId = "c1", localId = "c1", source = MessageSource.LocalOptimistic)))
        val sending = ChatReducer.reduce(queued, ChatDomainEvent.LocalSending("c1"))
        val sent = ChatReducer.reduce(
            sending,
            ChatDomainEvent.ServerMessageCreated(
                message = msg(clientId = "c1", backendId = "m1", status = ChatMessageStatus.SENT, source = MessageSource.HttpResponse),
                serverSequence = 5
            )
        )

        assertEquals(ChatMessageStatus.SENT, sent.single().status)
        assertEquals("m1", sent.single().backendId)
    }

    @Test
    fun sentDeliveredSeenWorks() {
        val sent = listOf(msg(backendId = "m1", status = ChatMessageStatus.SENT, source = MessageSource.HttpResponse, deliveryVersion = 1, serverSequence = 10))
        val delivered = ChatReducer.reduce(sent, msg(backendId = "m1", status = ChatMessageStatus.DELIVERED, source = MessageSource.SocketEvent, deliveryVersion = 2, serverSequence = 11))
        val seen = ChatReducer.reduce(delivered, ChatDomainEvent.ServerSeenUpdated(messageId = "m1", seenAt = 20, deliveryVersion = 3))

        assertEquals(ChatMessageStatus.SEEN, seen.single().status)
        assertEquals(3, seen.single().deliveryVersion)
    }

    @Test
    fun seenDoesNotDowngrade() {
        val seen = listOf(msg(backendId = "m1", status = ChatMessageStatus.SEEN, source = MessageSource.SocketEvent, deliveryVersion = 3, serverSequence = 9))
        val stale = msg(backendId = "m1", status = ChatMessageStatus.DELIVERED, source = MessageSource.LocalCache, deliveryVersion = 2, serverSequence = 8)

        val result = ChatReducer.reduce(seen, stale)

        assertEquals(ChatMessageStatus.SEEN, result.single().status)
    }

    @Test
    fun failedDoesNotOverrideSent() {
        val sent = listOf(msg(clientId = "c1", backendId = "m1", status = ChatMessageStatus.SENT, source = MessageSource.HttpResponse, serverSequence = 5))
        val result = ChatReducer.reduce(sent, ChatDomainEvent.LocalFailed(clientId = "c1", error = "timeout"))

        assertEquals(ChatMessageStatus.SENT, result.single().status)
    }

    @Test
    fun outOfOrderEditIgnored() {
        val current = listOf(msg(backendId = "m1", text = "new", source = MessageSource.SocketEvent, serverSequence = 20, editVersion = 2))
        val result = ChatReducer.reduce(
            current,
            ChatDomainEvent.ServerMessageEdited(
                messageId = "m1",
                text = "old",
                editVersion = 1,
                serverSequence = 10
            )
        )

        assertEquals("new", result.single().text)
        assertEquals(2, result.single().editVersion)
    }

    @Test
    fun deleteBeatsEdit() {
        val deleted = ChatReducer.reduce(
            listOf(msg(backendId = "m1", text = "hello", source = MessageSource.SocketEvent, serverSequence = 20)),
            ChatDomainEvent.ServerMessageDeleted(messageId = "m1", deleteSequence = 30)
        )
        val result = ChatReducer.reduce(
            deleted,
            ChatDomainEvent.ServerMessageEdited(
                messageId = "m1",
                text = "edited",
                editVersion = 5,
                serverSequence = 25
            )
        )

        assertEquals(30L, result.single().deleteSequence)
        assertEquals("hello", result.single().text)
    }

    @Test
    fun reactionVersionOverwriteWorks() {
        val current = listOf(msg(backendId = "m1", reactions = listOf("a"), hasReactionSnapshot = true, reactionVersion = 1, serverSequence = 10, source = MessageSource.SocketEvent))
        val result = ChatReducer.reduce(
            current,
            ChatDomainEvent.ServerReactionSnapshot(
                messageId = "m1",
                reactionsJson = "[\"b\"]",
                reactionVersion = 2,
                serverSequence = 11
            )
        )

        assertEquals(listOf("b"), result.single().reactions)
        assertEquals(2, result.single().reactionVersion)
    }

    @Test
    fun duplicateEventIgnored() {
        val current = listOf(msg(backendId = "m1", text = "hello", source = MessageSource.SocketEvent, serverSequence = 20))
        val result = ChatReducer.reduce(
            current,
            ChatDomainEvent.ServerMessageCreated(
                message = msg(backendId = "m1", text = "hello", source = MessageSource.SocketEvent, serverSequence = 20),
                serverSequence = 20
            )
        )

        assertEquals(1, result.size)
        assertEquals(20L, result.single().serverSequence)
    }

    @Test
    fun socketVsHttpRaceConverges() {
        val optimistic = listOf(msg(localId = "c1", clientId = "c1", source = MessageSource.LocalOptimistic, status = ChatMessageStatus.SENDING))
        val socket = ChatReducer.reduce(
            optimistic,
            ChatDomainEvent.ServerMessageCreated(
                message = msg(localId = "c1", clientId = "c1", backendId = "m1", source = MessageSource.SocketEvent, status = ChatMessageStatus.SENT),
                serverSequence = 7
            )
        )
        val http = ChatReducer.reduce(
            socket,
            ChatDomainEvent.ServerMessageCreated(
                message = msg(localId = "c1", clientId = "c1", backendId = "m1", source = MessageSource.HttpResponse, status = ChatMessageStatus.SENT),
                serverSequence = 7
            )
        )

        assertEquals(1, http.size)
        assertEquals("m1", http.single().backendId)
    }

    @Test
    fun reconnectEventGapRecoverySimulation() {
        val stale = listOf(msg(backendId = "m1", text = "old", source = MessageSource.LocalCache, serverSequence = 5))
        val recovered = ChatReducer.reduce(
            stale,
            ChatDomainEvent.ServerMessageCreated(
                message = msg(backendId = "m1", text = "new", source = MessageSource.ReconnectReconciliation, serverSequence = 9, editVersion = 2),
                serverSequence = 9
            )
        )

        assertEquals("new", recovered.single().text)
        assertEquals(9L, recovered.single().serverSequence)
    }

    @Test
    fun paginationDoesNotOverrideNewerState() {
        val current = listOf(msg(backendId = "m1", text = "fresh", source = MessageSource.SocketEvent, serverSequence = 20, editVersion = 2))
        val result = ChatReducer.reduce(
            current,
            ChatDomainEvent.ServerMessageCreated(
                message = msg(backendId = "m1", text = "stale", source = MessageSource.PaginationLoad, serverSequence = 10, editVersion = 1),
                serverSequence = 10
            )
        )

        assertEquals("fresh", result.single().text)
        assertEquals(20L, result.single().serverSequence)
    }

    @Test
    fun multiDeviceEditConflict() {
        val current = listOf(msg(backendId = "m1", text = "one", source = MessageSource.SocketEvent, serverSequence = 40, editVersion = 4))
        val result = ChatReducer.reduce(
            current,
            ChatDomainEvent.ServerMessageEdited(
                messageId = "m1",
                text = "two",
                editVersion = 5,
                serverSequence = 50
            )
        )

        assertEquals("two", result.single().text)
        assertEquals(5, result.single().editVersion)
    }

    @Test
    fun multiDeviceDeleteConflict() {
        val current = listOf(msg(backendId = "m1", text = "keep", source = MessageSource.SocketEvent, serverSequence = 40, editVersion = 4))
        val deleted = ChatReducer.reduce(current, ChatDomainEvent.ServerMessageDeleted(messageId = "m1", deleteSequence = 60))
        val result = ChatReducer.reduce(
            deleted,
            ChatDomainEvent.ServerMessageCreated(
                message = msg(backendId = "m1", text = "stale payload", source = MessageSource.ReconnectReconciliation, serverSequence = 50, editVersion = 5),
                serverSequence = 50
            )
        )

        assertEquals(60L, result.single().deleteSequence)
        assertEquals("keep", result.single().text)
    }

    @Test
    fun emptyServerReactionListClearsStaleLocalReactions() {
        val current = listOf(msg(backendId = "m1", reactions = listOf("u1:❤️"), hasReactionSnapshot = true, reactionVersion = 1, source = MessageSource.SocketEvent, serverSequence = 10))
        val result = ChatReducer.reduce(
            current,
            ChatDomainEvent.ServerReactionSnapshot(
                messageId = "m1",
                reactionsJson = "[]",
                reactionVersion = 2,
                serverSequence = 11
            )
        )
        assertTrue(result.single().reactions.isEmpty())
    }

    private fun msg(
        localId: String? = null,
        clientId: String? = null,
        backendId: String? = null,
        text: String = "hello",
        status: ChatMessageStatus = ChatMessageStatus.SENT,
        source: MessageSource = MessageSource.LocalCache,
        timestamp: Long = 1L,
        editedAt: Long? = null,
        deletedAt: Long? = null,
        reactions: List<String> = emptyList(),
        hasReactionSnapshot: Boolean = false,
        serverSequence: Long? = null,
        editVersion: Int = 0,
        reactionVersion: Int = 0,
        deliveryVersion: Int = 0,
        deleteSequence: Long? = null
    ) = ChatMessageSnapshot(
        localId = localId,
        clientId = clientId,
        backendId = backendId,
        text = text,
        status = status,
        source = source,
        timestamp = timestamp,
        editedAt = editedAt,
        deletedAt = deletedAt,
        reactions = reactions,
        hasReactionSnapshot = hasReactionSnapshot,
        serverSequence = serverSequence,
        editVersion = editVersion,
        reactionVersion = reactionVersion,
        deliveryVersion = deliveryVersion,
        deleteSequence = deleteSequence
    )
}
