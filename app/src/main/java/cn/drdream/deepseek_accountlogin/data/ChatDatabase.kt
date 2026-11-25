package cn.drdream.deepseek_accountlogin.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cn.drdream.deepseek_accountlogin.data.model.ChatSession

/** Single-table history store: sessions are small JSON documents. */
class ChatDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY NOT NULL,
                updated_at INTEGER NOT NULL,
                title TEXT NOT NULL,
                json TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_sessions_updated ON sessions(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first public schema.
    }

    @Synchronized
    fun loadAll(): List<ChatSession> {
        val result = ArrayList<ChatSession>()
        readableDatabase.rawQuery(
            "SELECT json FROM sessions ORDER BY updated_at DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                JsonCodec.decodeSession(cursor.getString(0))?.let(result::add)
            }
        }
        return result
    }

    @Synchronized
    fun upsert(session: ChatSession) {
        val values = ContentValues().apply {
            put("id", session.id)
            put("updated_at", session.updatedAt)
            put("title", session.title)
            put("json", JsonCodec.encodeSession(session))
        }
        writableDatabase.insertWithOnConflict(
            "sessions",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun delete(id: String) {
        writableDatabase.delete("sessions", "id = ?", arrayOf(id))
    }

    @Synchronized
    fun deleteAll() {
        writableDatabase.delete("sessions", null, null)
    }

    private companion object {
        const val DATABASE_NAME = "deepseek_chat.db"
        const val DATABASE_VERSION = 1
    }
}
