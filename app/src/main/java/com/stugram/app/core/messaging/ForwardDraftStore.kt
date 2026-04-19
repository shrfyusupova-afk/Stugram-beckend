package com.stugram.app.core.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ForwardDraft(
    val sourceMessageId: String,
    val sourceConversationId: String? = null,
    val sourceGroupId: String? = null,
    val sourceSenderName: String? = null,
    val sourceText: String? = null,
    val sourceMessageType: String = "text",
    val targetConversationId: String? = null,
    val targetGroupId: String? = null,
    val targetTitle: String? = null
)

object ForwardDraftStore {
    private val _draft = MutableStateFlow<ForwardDraft?>(null)
    val draft: StateFlow<ForwardDraft?> = _draft.asStateFlow()

    fun setDraft(draft: ForwardDraft) {
        _draft.value = draft
    }

    fun clear() {
        _draft.value = null
    }
}
