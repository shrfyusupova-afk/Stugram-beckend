package com.stugram.app.data.repository

import com.google.gson.JsonParser
import com.stugram.app.data.remote.model.ChatReplayEventModel
import com.stugram.app.domain.chat.ChatDomainEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplayEventMapperTest {
    @Test
    fun `maps created replay event into server created domain event`() {
        val payload = JsonParser.parseString(
            """
            {
              "message": {
                "_id": "message-1",
                "conversation": "conversation-1",
                "clientId": "client-1",
                "text": "hello",
                "messageType": "text",
                "createdAt": "2026-04-22T10:00:00Z",
                "serverSequence": 11
              }
            }
            """.trimIndent()
        ).asJsonObject

        val event = ChatReplayEventMapper.toDomainEvent(
            ChatReplayEventModel(
                sequence = 11,
                type = "message.created",
                targetType = "direct",
                targetId = "conversation-1",
                messageId = "message-1",
                clientId = "client-1",
                payload = payload
            )
        )

        assertTrue(event is ChatDomainEvent.ServerMessageCreated)
        val created = event as ChatDomainEvent.ServerMessageCreated
        assertEquals("message-1", created.message.backendId)
        assertEquals("client-1", created.message.clientId)
        assertEquals(11L, created.serverSequence)
    }

    @Test
    fun `maps reaction snapshot with empty server list as authoritative empty reactions`() {
        val payload = JsonParser.parseString(
            """
            {
              "messageId": "message-1",
              "reactions": [],
              "reactionVersion": 12,
              "serverSequence": 12
            }
            """.trimIndent()
        ).asJsonObject

        val event = ChatReplayEventMapper.toDomainEvent(
            ChatReplayEventModel(
                sequence = 12,
                type = "message.reactions",
                targetType = "direct",
                targetId = "conversation-1",
                messageId = "message-1",
                payload = payload
            )
        )

        assertTrue(event is ChatDomainEvent.ServerReactionSnapshot)
        val reactions = event as ChatDomainEvent.ServerReactionSnapshot
        assertEquals("[]", reactions.reactionsJson)
        assertEquals(12, reactions.reactionVersion)
        assertEquals(12L, reactions.serverSequence)
    }

    @Test
    fun `maps delete replay event into tombstone event`() {
        val event = ChatReplayEventMapper.toDomainEvent(
            ChatReplayEventModel(
                sequence = 13,
                type = "message.deleted",
                targetType = "group",
                targetId = "group-1",
                messageId = "message-1"
            )
        )

        assertTrue(event is ChatDomainEvent.ServerMessageDeleted)
        val deleted = event as ChatDomainEvent.ServerMessageDeleted
        assertEquals("message-1", deleted.messageId)
        assertEquals(13L, deleted.deleteSequence)
    }
}
