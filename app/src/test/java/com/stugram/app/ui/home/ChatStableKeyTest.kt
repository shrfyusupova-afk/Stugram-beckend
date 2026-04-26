package com.stugram.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatStableKeyTest {
    @Test
    fun directMessageStableKeyKeepsLocalIdentityAfterConfirmation() {
        val local = MessageData(id = 1, localId = "local-1", text = "", isMe = true)
        val confirmed = local.copy(backendId = "backend-1")

        assertEquals("local:local-1", local.stableKeyVm)
        assertEquals("local:local-1", confirmed.stableKeyVm)
    }

    @Test
    fun groupMessageStableKeyRemainsDeterministicAcrossUpdates() {
        val first = GroupMessageData(id = 7, localId = "local-7", text = "", senderName = "a", isMe = true)
        val second = first.copy(backendId = "backend-7", text = "updated")

        assertEquals("local:local-7", first.stableKeyVm)
        assertEquals("local:local-7", second.stableKeyVm)
    }
}
