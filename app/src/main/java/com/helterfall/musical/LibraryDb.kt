package com.helterfall.musical

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Track(
    val id: Long,
    val title: String,
    val author: String,
    val uri: String,
    val lyrics: String
)

data class Playlist(val id: Long, val name: String)

class LibraryDb(context: Context) :
    SQLiteOpenHelper(context, "musical.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE authors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                author_id INTEGER NOT NULL,
                uri TEXT NOT NULL,
                lyrics TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(author_id) REFERENCES authors(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE playlist_tracks (
                playlist_id INTEGER NOT NULL,
                track_id INTEGER NOT NULL,
                PRIMARY KEY(playlist_id, track_id),
                FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first release. Add migrations here when the schema changes.
    }

    private fun findOrCreateAuthor(db: SQLiteDatabase, name: String): Long {
        val cleanName = name.trim().ifBlank { "Неизвестный автор" }
        db.rawQuery("SELECT id FROM authors WHERE name = ?", arrayOf(cleanName)).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }

        val values = ContentValues().apply { put("name", cleanName) }
        db.insertWithOnConflict("authors", null, values, SQLiteDatabase.CONFLICT_IGNORE)

        db.rawQuery("SELECT id FROM authors WHERE name = ?", arrayOf(cleanName)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        }
    }

    fun addTrack(title: String, author: String, uri: String, lyrics: String): Long {
        val db = writableDatabase
        val authorId = findOrCreateAuthor(db, author)
        val values = ContentValues().apply {
            put("title", title.trim().ifBlank { "Без названия" })
            put("author_id", authorId)
            put("uri", uri)
            put("lyrics", lyrics)
        }
        return db.insert("tracks", null, values)
    }

    fun addAuthor(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        val values = ContentValues().apply { put("name", cleanName) }
        return writableDatabase.insertWithOnConflict(
            "authors", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun listAuthors(): List<String> {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name FROM authors ORDER BY name COLLATE NOCASE", null).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.getString(0))
        }
        return result
    }

    fun addPlaylist(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        val values = ContentValues().apply { put("name", cleanName) }
        return writableDatabase.insertWithOnConflict(
            "playlists", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun listPlaylists(): List<Playlist> {
        val result = mutableListOf<Playlist>()
        readableDatabase.rawQuery(
            "SELECT id, name FROM playlists ORDER BY name COLLATE NOCASE", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Playlist(cursor.getLong(0), cursor.getString(1)))
            }
        }
        return result
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
        val values = ContentValues().apply {
            put("playlist_id", playlistId)
            put("track_id", trackId)
        }
        return writableDatabase.insertWithOnConflict(
            "playlist_tracks", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun listTracks(playlistId: Long? = null): List<Track> {
        val result = mutableListOf<Track>()
        val baseQuery = """
            SELECT t.id, t.title, a.name, t.uri, t.lyrics
            FROM tracks t
            JOIN authors a ON a.id = t.author_id
        """.trimIndent()
        val query = if (playlistId == null) {
            "$baseQuery ORDER BY t.title COLLATE NOCASE"
        } else {
            """
            $baseQuery
            JOIN playlist_tracks pt ON pt.track_id = t.id
            WHERE pt.playlist_id = ?
            ORDER BY t.title COLLATE NOCASE
            """.trimIndent()
        }
        val args = if (playlistId == null) null else arrayOf(playlistId.toString())

        readableDatabase.rawQuery(query, args).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    Track(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        author = cursor.getString(2),
                        uri = cursor.getString(3),
                        lyrics = cursor.getString(4)
                    )
                )
            }
        }
        return result
    }
}
