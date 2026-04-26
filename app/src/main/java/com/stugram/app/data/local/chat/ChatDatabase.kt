package com.stugram.app.data.local.chat

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.stugram.app.core.observability.ChatReliabilityLogger
import com.stugram.app.core.observability.ChatReliabilityMetrics
import com.stugram.app.data.remote.model.ChatMessageModel
import com.stugram.app.data.remote.model.SendChatMessageRequest
import com.stugram.app.data.repository.PendingChatEnvelope
import com.stugram.app.data.repository.PendingChatPayload
import com.stugram.app.domain.chat.ChatDomainEvent
import com.stugram.app.domain.chat.ChatMessageSnapshot
import com.stugram.app.domain.chat.ChatMessageStatus
import com.stugram.app.domain.chat.ChatReducer
import com.stugram.app.domain.chat.MessageSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(
    tableName = "chat_messages",
    primaryKeys = ["scope", "targetId", "stableId"],
    indices = [
        Index(value = ["scope", "targetId", "serverSequence"]),
        Index(value = ["scope", "targetId", "backendId"]),
        Index(value = ["scope", "targetId", "clientId"]),
        Index(value = ["scope", "targetId", "timestamp"])
    ]
)
data class MessageEntity(
    val scope: String,
    val targetId: String,
    val stableId: String,
    val localId: String? = null,
    val clientId: String? = null,
    val backendId: String? = null,
    val rawJson: String? = null,
    val text: String = "",
    val status: String = ChatMessageStatus.SENT.name,
    val source: String = MessageSource.LocalCache.name,
    val timestamp: Long = 0L,
    val editedAt: Long? = null,
    val deletedAt: Long? = null,
    val reactionsJson: String = "[]",
    val hasReactionSnapshot: Boolean = false,
    val serverSequence: Long? = null,
    val editVersion: Int = 0,
    val reactionVersion: Int = 0,
    val deliveryVersion: Int = 0,
    val deleteSequence: Long? = null,
    val errorReason: String? = null,
    val retryCount: Int = 0
)

@Entity(
    tableName = "chat_pending_messages",
    primaryKeys = ["localId"],
    indices = [
        Index(value = ["scope", "targetId", "createdAt"]),
        Index(value = ["clientId"]),
        Index(value = ["nextAttemptAt"]),
        Index(value = ["status"])
    ]
)
data class PendingMessageEntity(
    val localId: String,
    val scope: String,
    val targetId: String,
    val clientId: String,
    val status: String,
    val retryCount: Int,
    val nextAttemptAt: Long?,
    val errorCode: String? = null,
    val terminalError: String? = null,
    val terminalFailedAt: Long? = null,
    val createdAt: Long,
    val text: String? = null,
    val replyToMessageId: String? = null,
    val mediaUri: String? = null,
    val mediaLocalPath: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val originalDisplayName: String? = null,
    val messageTypeOverride: String? = null
)

@Entity(
    tableName = "chat_event_cursors",
    primaryKeys = ["scope", "targetId"]
)
data class ChatEventCursorEntity(
    val scope: String,
    val targetId: String,
    val latestSequence: Long
)

@Entity(
    tableName = "chat_message_tombstones",
    primaryKeys = ["scope", "targetId", "backendId"],
    indices = [
        Index(value = ["scope", "targetId", "deleteSequence"])
    ]
)
data class MessageTombstoneEntity(
    val scope: String,
    val targetId: String,
    val backendId: String,
    val deleteSequence: Long,
    val deletedAt: Long
)

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM chat_messages
            WHERE scope = :scope AND targetId = :targetId
            ORDER BY COALESCE(serverSequence, 9223372036854775807) DESC, timestamp DESC, stableId DESC
            LIMIT :limit
        )
        ORDER BY COALESCE(serverSequence, 9223372036854775807) ASC, timestamp ASC, stableId ASC
        """
    )
    fun observeRecentMessages(scope: String, targetId: String, limit: Int): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId
        ORDER BY COALESCE(serverSequence, 9223372036854775807) ASC, timestamp ASC, stableId ASC
        """
    )
    fun observeMessages(scope: String, targetId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId
        ORDER BY COALESCE(serverSequence, 9223372036854775807) ASC, timestamp ASC, stableId ASC
        """
    )
    suspend fun getMessages(scope: String, targetId: String): List<MessageEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM chat_messages
            WHERE scope = :scope AND targetId = :targetId
            ORDER BY COALESCE(serverSequence, 9223372036854775807) DESC, timestamp DESC, stableId DESC
            LIMIT :limit
        )
        ORDER BY COALESCE(serverSequence, 9223372036854775807) ASC, timestamp ASC, stableId ASC
        """
    )
    suspend fun getRecentMessages(scope: String, targetId: String, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE scope = :scope AND targetId = :targetId")
    suspend fun countForTarget(scope: String, targetId: String): Int

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId AND stableId = :stableId
        LIMIT 1
        """
    )
    suspend fun getByStableId(scope: String, targetId: String, stableId: String): MessageEntity?

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId AND backendId = :backendId
        LIMIT 1
        """
    )
    suspend fun getByBackendId(scope: String, targetId: String, backendId: String): MessageEntity?

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId AND clientId = :clientId
        LIMIT 1
        """
    )
    suspend fun getByClientId(scope: String, targetId: String, clientId: String): MessageEntity?

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId AND localId = :localId
        LIMIT 1
        """
    )
    suspend fun getByLocalId(scope: String, targetId: String, localId: String): MessageEntity?

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId AND clientId = :clientId AND backendId IS NOT NULL
        LIMIT 1
        """
    )
    suspend fun findConfirmedByClientId(scope: String, targetId: String, clientId: String): MessageEntity?

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query(
        """
        DELETE FROM chat_messages
        WHERE scope = :scope AND targetId = :targetId AND stableId IN (:stableIds)
        """
    )
    suspend fun deleteByStableIds(scope: String, targetId: String, stableIds: List<String>)

    @Query("DELETE FROM chat_messages WHERE scope = :scope AND targetId = :targetId")
    suspend fun deleteForTarget(scope: String, targetId: String)
}

@Dao
interface PendingMessageDao {
    @Query(
        """
        SELECT * FROM chat_pending_messages
        WHERE scope = :scope AND targetId = :targetId
        ORDER BY createdAt ASC
        """
    )
    fun observePending(scope: String, targetId: String): Flow<List<PendingMessageEntity>>

    @Query(
        """
        SELECT * FROM chat_pending_messages
        WHERE terminalError IS NULL
        ORDER BY createdAt ASC
        """
    )
    suspend fun getActivePending(): List<PendingMessageEntity>

    @Query(
        """
        SELECT * FROM chat_pending_messages
        WHERE scope = :scope AND targetId = :targetId
        ORDER BY createdAt ASC
        """
    )
    suspend fun getForTarget(scope: String, targetId: String): List<PendingMessageEntity>

    @Query("SELECT * FROM chat_pending_messages WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): PendingMessageEntity?

    @Query("SELECT COUNT(*) FROM chat_pending_messages WHERE terminalError IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT MIN(createdAt) FROM chat_pending_messages WHERE terminalError IS NULL")
    suspend fun oldestActiveCreatedAt(): Long?

    @Upsert
    suspend fun upsert(entity: PendingMessageEntity)

    @Query("DELETE FROM chat_pending_messages WHERE localId = :localId")
    suspend fun delete(localId: String)
}

@Dao
interface CursorDao {
    @Query("SELECT * FROM chat_event_cursors WHERE scope = :scope AND targetId = :targetId LIMIT 1")
    suspend fun get(scope: String, targetId: String): ChatEventCursorEntity?

    @Upsert
    suspend fun upsert(entity: ChatEventCursorEntity)
}

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM chat_message_tombstones WHERE scope = :scope AND targetId = :targetId AND backendId = :backendId LIMIT 1")
    suspend fun get(scope: String, targetId: String, backendId: String): MessageTombstoneEntity?

    @Query("SELECT * FROM chat_message_tombstones WHERE scope = :scope AND targetId = :targetId")
    suspend fun getForTarget(scope: String, targetId: String): List<MessageTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MessageTombstoneEntity)
}

@Database(
    entities = [
        MessageEntity::class,
        PendingMessageEntity::class,
        ChatEventCursorEntity::class,
        MessageTombstoneEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun cursorDao(): CursorDao
    abstract fun tombstoneDao(): TombstoneDao

    companion object {
        @Volatile
        private var instance: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "stugram_chat_room.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_pending_messages_new (
                        localId TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        targetId TEXT NOT NULL,
                        clientId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        retryCount INTEGER NOT NULL,
                        nextAttemptAt INTEGER,
                        errorCode TEXT,
                        terminalError TEXT,
                        terminalFailedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        text TEXT,
                        replyToMessageId TEXT,
                        mediaUri TEXT,
                        mediaLocalPath TEXT,
                        mimeType TEXT,
                        messageTypeOverride TEXT,
                        PRIMARY KEY(localId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO chat_pending_messages_new (
                        localId, scope, targetId, clientId, status, retryCount, nextAttemptAt,
                        errorCode, terminalError, terminalFailedAt, createdAt, text,
                        replyToMessageId, mediaUri, mediaLocalPath, mimeType, messageTypeOverride
                    )
                    SELECT
                        localId, scope, targetId, clientId, status, retryCount, nextAttemptAt,
                        errorCode, terminalError, terminalFailedAt, createdAt, text,
                        replyToMessageId, mediaUri, NULL, mimeType, messageTypeOverride
                    FROM chat_pending_messages
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE chat_pending_messages")
                db.execSQL("ALTER TABLE chat_pending_messages_new RENAME TO chat_pending_messages")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_pending_messages_scope_targetId_createdAt ON chat_pending_messages(scope, targetId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_pending_messages_clientId ON chat_pending_messages(clientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_pending_messages_nextAttemptAt ON chat_pending_messages(nextAttemptAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_pending_messages_status ON chat_pending_messages(status)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_pending_messages ADD COLUMN fileSize INTEGER")
                db.execSQL("ALTER TABLE chat_pending_messages ADD COLUMN originalDisplayName TEXT")
            }
        }
    }
}

class ChatRoomStore private constructor(
    private val db: ChatDatabase,
    private val gson: Gson = Gson()
) {
    fun observeMessages(scope: String, targetId: String): Flow<List<MessageEntity>> =
        db.messageDao().observeMessages(scope, targetId)

    fun observeMessages(scope: String, targetId: String, limit: Int): Flow<List<MessageEntity>> =
        db.messageDao().observeRecentMessages(scope, targetId, limit.coerceAtLeast(1))

    fun observeMessageModels(scope: String, targetId: String): Flow<List<ChatMessageModel>> =
        observeMessages(scope, targetId).map { entities ->
            entities.mapNotNull { it.toChatMessageModelOrNull(gson) }
        }

    fun observeMessageModels(scope: String, targetId: String, limit: Int): Flow<List<ChatMessageModel>> =
        observeMessages(scope, targetId, limit).map { entities ->
            entities.mapNotNull { it.toChatMessageModelOrNull(gson) }
        }

    fun observePending(scope: String, targetId: String): Flow<List<PendingChatEnvelope>> =
        db.pendingMessageDao().observePending(scope, targetId).map { items ->
            items.map { it.toEnvelope() }
        }

    suspend fun loadMessageModels(scope: String, targetId: String): List<ChatMessageModel> =
        db.messageDao().getMessages(scope, targetId).mapNotNull { it.toChatMessageModelOrNull(gson) }

    suspend fun loadMessageModels(scope: String, targetId: String, limit: Int): List<ChatMessageModel> =
        db.messageDao().getRecentMessages(scope, targetId, limit.coerceAtLeast(1)).mapNotNull { it.toChatMessageModelOrNull(gson) }

    suspend fun countMessages(scope: String, targetId: String): Int =
        db.messageDao().countForTarget(scope, targetId)

    suspend fun loadPending(scope: String, targetId: String): List<PendingChatEnvelope> =
        db.pendingMessageDao().getForTarget(scope, targetId).map { it.toEnvelope() }

    suspend fun loadActivePending(): List<PendingChatEnvelope> =
        db.pendingMessageDao().getActivePending().map { it.toEnvelope() }

    suspend fun upsertPending(scope: String, targetId: String, localId: String, payload: PendingChatPayload) {
        db.withTransaction {
            val existing = db.pendingMessageDao().getByLocalId(localId)
            db.pendingMessageDao().upsert(
                PendingMessageEntity(
                    localId = localId,
                    scope = scope,
                    targetId = targetId,
                    clientId = localId,
                    status = (existing?.status ?: ChatMessageStatus.QUEUED.name),
                    retryCount = existing?.retryCount ?: 0,
                    nextAttemptAt = existing?.nextAttemptAt ?: System.currentTimeMillis(),
                    errorCode = existing?.errorCode,
                    terminalError = null,
                    terminalFailedAt = null,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    text = payload.text,
                    replyToMessageId = payload.replyToMessageId,
                    mediaUri = payload.mediaUri,
                    mediaLocalPath = payload.mediaLocalPath,
                    mimeType = payload.mimeType,
                    fileSize = payload.fileSize,
                    originalDisplayName = payload.originalDisplayName,
                    messageTypeOverride = payload.messageTypeOverride
                )
            )
            updatePendingMetricsInTransaction()
        }
    }

    suspend fun markPendingRetry(
        localId: String,
        nextAttemptAt: Long = System.currentTimeMillis(),
        errorCode: String? = null
    ) {
        db.withTransaction {
            val existing = db.pendingMessageDao().getByLocalId(localId) ?: return@withTransaction
            db.pendingMessageDao().upsert(
                existing.copy(
                    status = ChatMessageStatus.QUEUED.name,
                    retryCount = existing.retryCount + 1,
                    nextAttemptAt = nextAttemptAt,
                    errorCode = errorCode,
                    terminalError = null,
                    terminalFailedAt = null
                )
            )
            updatePendingMetricsInTransaction()
        }
    }

    suspend fun markPendingSending(localId: String) {
        db.withTransaction {
            val existing = db.pendingMessageDao().getByLocalId(localId) ?: return@withTransaction
            db.pendingMessageDao().upsert(
                existing.copy(
                    status = ChatMessageStatus.SENDING.name,
                    nextAttemptAt = System.currentTimeMillis(),
                    errorCode = null
                )
            )
            updatePendingMetricsInTransaction()
        }
    }

    suspend fun markTerminalFailure(localId: String, message: String) {
        db.withTransaction {
            val existing = db.pendingMessageDao().getByLocalId(localId) ?: return@withTransaction
            db.pendingMessageDao().upsert(
                existing.copy(
                    status = ChatMessageStatus.FAILED.name,
                    retryCount = existing.retryCount + 1,
                    errorCode = "TERMINAL_FAILURE",
                    terminalError = message,
                    terminalFailedAt = System.currentTimeMillis()
                )
            )
            updatePendingMetricsInTransaction()
        }
    }

    suspend fun removePending(localId: String) {
        db.withTransaction {
            db.pendingMessageDao().delete(localId)
            updatePendingMetricsInTransaction()
        }
    }

    suspend fun confirmPendingSuccess(
        scope: String,
        targetId: String,
        localId: String,
        model: ChatMessageModel,
        source: MessageSource = MessageSource.HttpResponse
    ) {
        // Worker success is intentionally transactional: if reducer/Room persistence fails,
        // the pending row remains and WorkManager can retry instead of making the message vanish.
        db.withTransaction {
            val confirmedModel = if (model.clientId.isNullOrBlank()) {
                model.copy(clientId = localId)
            } else {
                model
            }
            val confirmedEvent = ChatDomainEvent.ServerMessageCreated(
                message = confirmedModel.toSnapshot(source, gson),
                serverSequence = confirmedModel.updatedAt.toEpochMillisOrNull() ?: confirmedModel.createdAt.toEpochMillisOrNow()
            )
            ChatReliabilityLogger.info("chat_store_apply_event_started", buildEventFields(scope, targetId, confirmedEvent))
            applyEventInTransaction(
                scope = scope,
                targetId = targetId,
                event = confirmedEvent
            )
            db.pendingMessageDao().delete(localId)
            updatePendingMetricsInTransaction()
            ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to scope, "targetId" to targetId, "localId" to localId, "reason" to "confirm_pending_success"))
            ChatReliabilityLogger.info("chat_store_apply_event_succeeded", buildEventFields(scope, targetId, confirmedEvent))
        }
    }

    suspend fun markTerminalFailureAndRemove(
        localId: String,
        errorCode: String,
        message: String
    ) {
        db.withTransaction {
            val pending = db.pendingMessageDao().getByLocalId(localId) ?: return@withTransaction
            val failedAt = System.currentTimeMillis()
            val failedSnapshot = ChatMessageSnapshot(
                localId = pending.localId,
                clientId = pending.clientId,
                text = pending.text.orEmpty(),
                status = ChatMessageStatus.FAILED,
                source = MessageSource.LocalRetry,
                timestamp = pending.createdAt,
                errorReason = message,
                retryCount = pending.retryCount + 1
            )
            applyEventInTransaction(
                pending.scope,
                pending.targetId,
                ChatDomainEvent.LocalQueued(failedSnapshot.copy(status = ChatMessageStatus.QUEUED))
            )
            applyEventInTransaction(
                pending.scope,
                pending.targetId,
                ChatDomainEvent.LocalFailed(pending.clientId, message)
            )
            db.pendingMessageDao().upsert(
                pending.copy(
                    status = ChatMessageStatus.FAILED.name,
                    retryCount = pending.retryCount + 1,
                    nextAttemptAt = null,
                    errorCode = errorCode,
                    terminalError = message,
                    terminalFailedAt = failedAt
                )
            )
            db.pendingMessageDao().delete(localId)
            updatePendingMetricsInTransaction()
            ChatReliabilityLogger.info("chat_pending_removed", mapOf("targetType" to pending.scope, "targetId" to pending.targetId, "localId" to localId, "reason" to "terminal_failure"))
        }
    }

    suspend fun hasConfirmedMessage(scope: String, targetId: String, clientId: String): Boolean =
        db.messageDao().findConfirmedByClientId(scope, targetId, clientId) != null

    suspend fun currentCursor(scope: String, targetId: String): Long =
        db.cursorDao().get(scope, targetId)?.latestSequence ?: 0L

    suspend fun applySnapshot(scope: String, targetId: String, snapshot: ChatMessageSnapshot) {
        applyEvent(scope, targetId, snapshot.toDomainEvent())
    }

    suspend fun applyRemoteMessage(
        scope: String,
        targetId: String,
        model: ChatMessageModel,
        source: MessageSource,
        serverSequence: Long? = null
    ) {
        val snapshot = model.toSnapshot(source, gson)
        applyEvent(scope, targetId, ChatDomainEvent.ServerMessageCreated(snapshot, serverSequence ?: snapshot.serverSequence))
    }

    suspend fun applyDelete(scope: String, targetId: String, messageId: String, deleteSequence: Long = System.currentTimeMillis()) {
        applyEvent(scope, targetId, ChatDomainEvent.ServerMessageDeleted(messageId, deleteSequence))
    }

    suspend fun applyDelivered(
        scope: String,
        targetId: String,
        messageId: String,
        deliveredAt: Long = System.currentTimeMillis()
    ) {
        applyEvent(
            scope,
            targetId,
            ChatDomainEvent.ServerDeliveredUpdated(
                messageId = messageId,
                deliveredAt = deliveredAt,
                deliveryVersion = 1,
                serverSequence = deliveredAt
            )
        )
    }

    suspend fun applyEvent(scope: String, targetId: String, event: ChatDomainEvent) {
        ChatReliabilityLogger.info("chat_store_apply_event_started", buildEventFields(scope, targetId, event))
        runCatching {
            db.withTransaction {
                applyEventInTransaction(scope, targetId, event)
            }
        }.onSuccess {
            ChatReliabilityLogger.info("chat_store_apply_event_succeeded", buildEventFields(scope, targetId, event))
        }.onFailure { error ->
            ChatReliabilityLogger.error(
                "chat_store_apply_event_failed",
                buildEventFields(scope, targetId, event) + mapOf("errorCode" to (error.message ?: error::class.java.simpleName))
            )
            throw error
        }
    }

    private suspend fun applyEventInTransaction(scope: String, targetId: String, event: ChatDomainEvent) {
        val currentEntities = loadCandidateEntities(scope, targetId, event)
        val current = currentEntities.map { it.toSnapshot() }
        val updated = ChatReducer.reduce(current, event)
        val tombstones = loadRelevantTombstones(scope, targetId, event).associateBy { it.backendId }
        val entities = updated.map { snapshot ->
            val effective = snapshot.enforceTombstone(tombstones[snapshot.backendId])
            effective.toEntity(scope, targetId)
        }
        if (entities.isNotEmpty()) {
            db.messageDao().upsertAll(entities)
        }
        val staleStableIds = currentEntities.map { it.stableId }.toSet() - entities.map { it.stableId }.toSet()
        if (staleStableIds.isNotEmpty()) {
            db.messageDao().deleteByStableIds(scope, targetId, staleStableIds.toList())
        }
        if (event is ChatDomainEvent.ServerMessageDeleted) {
            db.tombstoneDao().upsert(
                MessageTombstoneEntity(
                    scope = scope,
                    targetId = targetId,
                    backendId = event.messageId,
                    deleteSequence = event.deleteSequence,
                    deletedAt = event.deleteSequence
                )
            )
        }
        event.sequenceForCursor()?.let { sequence ->
            val currentCursor = db.cursorDao().get(scope, targetId)?.latestSequence ?: 0L
            if (sequence > currentCursor) {
                db.cursorDao().upsert(ChatEventCursorEntity(scope, targetId, sequence))
            }
        }
        event.clientIdForPendingCleanup()?.let { clientId ->
            if (db.messageDao().findConfirmedByClientId(scope, targetId, clientId) != null) {
                db.pendingMessageDao().delete(clientId)
                updatePendingMetricsInTransaction()
            }
        }
    }

    private suspend fun updatePendingMetricsInTransaction() {
        val activeCount = db.pendingMessageDao().countActive()
        val oldestCreatedAt = db.pendingMessageDao().oldestActiveCreatedAt()
        val oldestAge = oldestCreatedAt?.let { System.currentTimeMillis() - it } ?: 0L
        ChatReliabilityMetrics.setGauge("chat_pending_queue_depth", activeCount.toLong())
        ChatReliabilityMetrics.setGauge("chat_pending_oldest_age_ms", oldestAge)
    }

    private fun buildEventFields(scope: String, targetId: String, event: ChatDomainEvent): Map<String, Any?> = mapOf(
        "targetType" to scope,
        "targetId" to targetId,
        "eventType" to event.javaClass.simpleName,
        "clientId" to event.clientIdForPendingCleanup(),
        "backendMessageId" to event.backendMessageIdForLogging(),
        "serverSequence" to event.sequenceForCursor()
    )

    private suspend fun loadCandidateEntities(
        scope: String,
        targetId: String,
        event: ChatDomainEvent
    ): List<MessageEntity> {
        val messageDao = db.messageDao()
        val stableIds = linkedSetOf<String>()
        val results = linkedMapOf<String, MessageEntity>()

        suspend fun addStable(stableId: String?) {
            val key = stableId?.takeIf { it.isNotBlank() } ?: return
            if (!stableIds.add(key)) return
            messageDao.getByStableId(scope, targetId, key)?.let { results[it.stableId] = it }
        }

        suspend fun addBackend(backendId: String?) {
            val key = backendId?.takeIf { it.isNotBlank() } ?: return
            messageDao.getByBackendId(scope, targetId, key)?.let { entity ->
                results[entity.stableId] = entity
                stableIds += entity.stableId
            }
        }

        suspend fun addClient(clientId: String?) {
            val key = clientId?.takeIf { it.isNotBlank() } ?: return
            messageDao.getByClientId(scope, targetId, key)?.let { entity ->
                results[entity.stableId] = entity
                stableIds += entity.stableId
            }
        }

        suspend fun addLocal(localId: String?) {
            val key = localId?.takeIf { it.isNotBlank() } ?: return
            messageDao.getByLocalId(scope, targetId, key)?.let { entity ->
                results[entity.stableId] = entity
                stableIds += entity.stableId
            }
        }

        when (event) {
            is ChatDomainEvent.LocalQueued -> {
                addStable(event.message.stableId)
                addClient(event.message.clientId)
                addLocal(event.message.localId)
                addBackend(event.message.backendId)
            }
            is ChatDomainEvent.LocalSending -> {
                addStable(event.clientId)
                addClient(event.clientId)
                addLocal(event.clientId)
            }
            is ChatDomainEvent.LocalFailed -> {
                addStable(event.clientId)
                addClient(event.clientId)
                addLocal(event.clientId)
            }
            is ChatDomainEvent.ServerMessageCreated -> {
                addStable(event.message.stableId)
                addClient(event.message.clientId)
                addLocal(event.message.localId)
                addBackend(event.message.backendId)
            }
            is ChatDomainEvent.ServerMessageEdited -> addBackend(event.messageId)
            is ChatDomainEvent.ServerMessageDeleted -> addBackend(event.messageId)
            is ChatDomainEvent.ServerReactionSnapshot -> addBackend(event.messageId)
            is ChatDomainEvent.ServerDeliveredUpdated -> addBackend(event.messageId)
            is ChatDomainEvent.ServerSeenUpdated -> addBackend(event.messageId)
        }

        return results.values.toList()
    }

    private suspend fun loadRelevantTombstones(
        scope: String,
        targetId: String,
        event: ChatDomainEvent
    ): List<MessageTombstoneEntity> {
        val backendIds = buildList {
            when (event) {
                is ChatDomainEvent.LocalQueued -> event.message.backendId?.let(::add)
                is ChatDomainEvent.ServerMessageCreated -> event.message.backendId?.let(::add)
                is ChatDomainEvent.ServerMessageEdited -> add(event.messageId)
                is ChatDomainEvent.ServerMessageDeleted -> add(event.messageId)
                is ChatDomainEvent.ServerReactionSnapshot -> add(event.messageId)
                is ChatDomainEvent.ServerDeliveredUpdated -> add(event.messageId)
                is ChatDomainEvent.ServerSeenUpdated -> add(event.messageId)
                else -> Unit
            }
        }.distinct()

        if (backendIds.isEmpty()) return emptyList()
        return backendIds.mapNotNull { backendId ->
            db.tombstoneDao().get(scope, targetId, backendId)
        }
    }

    companion object {
        @Volatile
        private var instance: ChatRoomStore? = null

        fun fromDatabase(db: ChatDatabase): ChatRoomStore = ChatRoomStore(db)

        fun get(context: Context): ChatRoomStore =
            instance ?: synchronized(this) {
                instance ?: ChatRoomStore(ChatDatabase.getInstance(context.applicationContext)).also { instance = it }
            }
    }
}

private fun MessageEntity.toSnapshot(): ChatMessageSnapshot =
    ChatMessageSnapshot(
        localId = localId,
        clientId = clientId,
        backendId = backendId,
        rawJson = rawJson,
        text = text,
        status = enumValueOf(status),
        source = enumValueOf(source),
        timestamp = timestamp,
        editedAt = editedAt,
        deletedAt = deletedAt,
        reactions = parseReactionsJson(reactionsJson),
        hasReactionSnapshot = hasReactionSnapshot,
        serverSequence = serverSequence,
        editVersion = editVersion,
        reactionVersion = reactionVersion,
        deliveryVersion = deliveryVersion,
        deleteSequence = deleteSequence,
        errorReason = errorReason,
        retryCount = retryCount
    )

private fun ChatMessageSnapshot.toEntity(scope: String, targetId: String): MessageEntity =
    MessageEntity(
        scope = scope,
        targetId = targetId,
        stableId = stableId ?: (backendId ?: localId ?: clientId ?: "$scope:$targetId:$timestamp"),
        localId = localId,
        clientId = clientId ?: localId,
        backendId = backendId,
        rawJson = rawJson,
        text = text,
        status = status.name,
        source = source.name,
        timestamp = timestamp,
        editedAt = editedAt,
        deletedAt = deletedAt,
        reactionsJson = reactions.toJsonList(),
        hasReactionSnapshot = hasReactionSnapshot,
        serverSequence = serverSequence,
        editVersion = editVersion,
        reactionVersion = reactionVersion,
        deliveryVersion = deliveryVersion,
        deleteSequence = deleteSequence,
        errorReason = errorReason,
        retryCount = retryCount
    )

private fun ChatMessageSnapshot.enforceTombstone(tombstone: MessageTombstoneEntity?): ChatMessageSnapshot {
    if (tombstone == null) return this
    if (deleteSequence != null && deleteSequence >= tombstone.deleteSequence) return this
    return copy(
        deletedAt = tombstone.deletedAt,
        deleteSequence = tombstone.deleteSequence,
        text = "",
        reactions = emptyList(),
        hasReactionSnapshot = true
    )
}

private fun ChatMessageModel.toSnapshot(source: MessageSource, gson: Gson): ChatMessageSnapshot =
    ChatMessageSnapshot(
        localId = clientId,
        clientId = clientId,
        backendId = id,
        rawJson = gson.toJson(this),
        text = text.orEmpty(),
        status = when {
            readAt != null -> ChatMessageStatus.SEEN
            deliveredAt != null -> ChatMessageStatus.DELIVERED
            else -> ChatMessageStatus.SENT
        },
        source = source,
        timestamp = createdAt.toEpochMillisOrNow(),
        editedAt = editedAt.toEpochMillisOrNull(),
        deletedAt = deletedForEveryoneAt.toEpochMillisOrNull(),
        reactions = reactions.map { "${it.user?.id.orEmpty()}:${it.emoji}" },
        hasReactionSnapshot = true,
        serverSequence = serverSequence ?: updatedAt.toEpochMillisOrNull() ?: createdAt.toEpochMillisOrNow(),
        editVersion = editVersion ?: if (editedAt != null) 1 else 0,
        reactionVersion = reactionVersion ?: if (reactions.isNotEmpty()) 1 else 0,
        deliveryVersion = when {
            deliveryVersion != null -> deliveryVersion
            readAt != null -> 2
            deliveredAt != null -> 1
            else -> 0
        },
        deleteSequence = deleteSequence ?: deletedForEveryoneAt.toEpochMillisOrNull()
    )

private fun MessageEntity.toChatMessageModelOrNull(gson: Gson): ChatMessageModel? {
    if (backendId == null || deleteSequence != null || deletedAt != null) return null
    val raw = rawJson ?: return null
    return runCatching { gson.fromJson(raw, ChatMessageModel::class.java) }.getOrNull()?.copy(
        clientId = clientId,
        text = text.takeIf { it.isNotBlank() } ?: "",
        reactions = emptyList(),
        editedAt = editedAt?.let { java.time.Instant.ofEpochMilli(it).toString() },
        deliveredAt = if (deliveryVersion > 0 && status == ChatMessageStatus.DELIVERED.name) {
            java.time.Instant.ofEpochMilli(timestamp).toString()
        } else {
            null
        },
        readAt = if (deliveryVersion > 0 && status == ChatMessageStatus.SEEN.name) {
            java.time.Instant.ofEpochMilli(timestamp).toString()
        } else {
            null
        },
        deletedForEveryoneAt = deletedAt?.let { java.time.Instant.ofEpochMilli(it).toString() },
        updatedAt = serverSequence?.let { java.time.Instant.ofEpochMilli(it).toString() }
    )
}

private fun PendingMessageEntity.toEnvelope(): PendingChatEnvelope =
    PendingChatEnvelope(
        localId = localId,
        scope = scope,
        targetId = targetId,
        status = status,
        payload = PendingChatPayload(
            text = text,
            replyToMessageId = replyToMessageId,
            mediaUri = mediaUri,
            mediaLocalPath = mediaLocalPath,
            mimeType = mimeType,
            fileSize = fileSize,
            originalDisplayName = originalDisplayName,
            messageTypeOverride = messageTypeOverride
        ),
        createdAt = createdAt,
        retryCount = retryCount,
        nextAttemptAt = nextAttemptAt,
        errorCode = errorCode,
        terminalError = terminalError,
        terminalFailedAt = terminalFailedAt
    )

private fun ChatDomainEvent.sequenceForCursor(): Long? = when (this) {
    is ChatDomainEvent.ServerMessageCreated -> serverSequence ?: message.serverSequence
    is ChatDomainEvent.ServerMessageEdited -> serverSequence
    is ChatDomainEvent.ServerMessageDeleted -> deleteSequence
    is ChatDomainEvent.ServerReactionSnapshot -> serverSequence
    is ChatDomainEvent.ServerDeliveredUpdated -> serverSequence
    is ChatDomainEvent.ServerSeenUpdated -> serverSequence
    else -> null
}

private fun ChatDomainEvent.clientIdForPendingCleanup(): String? = when (this) {
    is ChatDomainEvent.ServerMessageCreated -> message.clientId ?: message.localId
    else -> null
}

private fun ChatDomainEvent.backendMessageIdForLogging(): String? = when (this) {
    is ChatDomainEvent.ServerMessageCreated -> message.backendId
    is ChatDomainEvent.ServerMessageEdited -> messageId
    is ChatDomainEvent.ServerMessageDeleted -> messageId
    is ChatDomainEvent.ServerReactionSnapshot -> messageId
    is ChatDomainEvent.ServerDeliveredUpdated -> messageId
    is ChatDomainEvent.ServerSeenUpdated -> messageId
    else -> null
}

private fun ChatMessageSnapshot.toDomainEvent(): ChatDomainEvent = when (source) {
    MessageSource.LocalOptimistic -> ChatDomainEvent.LocalQueued(copy(status = ChatMessageStatus.QUEUED))
    MessageSource.LocalRetry -> if (status == ChatMessageStatus.FAILED) {
        ChatDomainEvent.LocalFailed(clientId ?: localId.orEmpty(), errorReason)
    } else {
        ChatDomainEvent.LocalSending(clientId ?: localId.orEmpty())
    }
    MessageSource.LocalCache,
    MessageSource.HttpResponse,
    MessageSource.SocketEvent,
    MessageSource.ReconnectReconciliation,
    MessageSource.PaginationLoad -> ChatDomainEvent.ServerMessageCreated(this, serverSequence)
}

private fun parseReactionsJson(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed == "[]") return emptyList()
    return trimmed.removePrefix("[").removeSuffix("]")
        .split(",")
        .map { it.trim().removePrefix("\"").removeSuffix("\"") }
        .filter { it.isNotBlank() }
}

private fun List<String>.toJsonList(): String =
    joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }

private fun String?.toEpochMillisOrNow(): Long =
    toEpochMillisOrNull() ?: System.currentTimeMillis()

private fun String?.toEpochMillisOrNull(): Long? {
    if (this.isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()
}
