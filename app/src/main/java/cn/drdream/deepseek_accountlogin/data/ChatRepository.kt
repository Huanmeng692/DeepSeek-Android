package cn.drdream.deepseek_accountlogin.data

import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** In-memory session cache over SQLite. All model objects are immutable. */
class ChatRepository(private val database: ChatDatabase) {

    private val _sessions = MutableStateFlow(database.loadAll())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    fun find(id: String?): ChatSession? = _sessions.value.firstOrNull { it.id == id }

    fun createSession(): ChatSession {
        val now = System.currentTimeMillis()
        val session = ChatSession(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
        save(session, touch = false)
        return session
    }

    /**
     * Persists a session and republishes it. Streaming updates pass `touch = false`
     * so sorting does not jump on every token; the final save touches the timestamp.
     */
    fun save(session: ChatSession, touch: Boolean = true) {
        val stored = if (touch) session.copy(updatedAt = System.currentTimeMillis()) else session
        database.upsert(stored)
        _sessions.update { current ->
            (current.filterNot { it.id == stored.id } + stored)
                .sortedByDescending { it.updatedAt }
        }
    }

    fun update(sessionId: String, touch: Boolean = true, transform: (ChatSession) -> ChatSession) {
        val current = find(sessionId) ?: return
        save(transform(current), touch)
    }

    /** Republish a streaming snapshot without hitting SQLite on every token. */
    fun publish(session: ChatSession) {
        _sessions.update { current ->
            (current.filterNot { it.id == session.id } + session)
                .sortedByDescending { it.updatedAt }
        }
    }

    fun rename(sessionId: String, title: String) {
        update(sessionId) { it.copy(title = title.trim().take(80)) }
    }

    fun delete(sessionId: String) {
        database.delete(sessionId)
        _sessions.update { current -> current.filterNot { it.id == sessionId } }
    }

    fun deleteAll() {
        database.deleteAll()
        _sessions.value = emptyList()
    }
}
