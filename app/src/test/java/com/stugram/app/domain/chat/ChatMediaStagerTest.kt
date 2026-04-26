package com.stugram.app.domain.chat

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatMediaStagerTest {
    @Test
    fun copyToPrivateStorageCopiesMediaIntoClientDirectory() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val clientId = "client-media-1"
        ChatMediaStager.cleanup(context, clientId)
        val source = File(context.cacheDir, "source-media.bin").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }

        val staged = ChatMediaStager.copyToPrivateStorage(
            context = context,
            source = Uri.fromFile(source),
            clientId = clientId,
            providedMimeType = "image/jpeg"
        )

        val stagedFile = File(staged.localPath)
        assertTrue(stagedFile.absolutePath.contains("/files/chat_media/$clientId/original"))
        assertTrue(stagedFile.exists())
        assertEquals("image/jpeg", staged.mimeType)
        assertEquals(5L, staged.fileSize)
        assertArrayEquals(source.readBytes(), stagedFile.readBytes())

        ChatMediaStager.cleanup(context, clientId)
        assertFalse(ChatMediaStager.clientDirectory(context, clientId).exists())
        source.delete()
        }
    }
}
