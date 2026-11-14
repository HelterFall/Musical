package com.helterfall.musical

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var database: LibraryDb
    private lateinit var trackList: LinearLayout
    private lateinit var playlistSpinner: Spinner
    private lateinit var countLabel: TextView

    private var playlists: List<Playlist> = emptyList()
    private var selectedPlaylistId: Long? = null
    private var isRefreshingSpinner = false

    private var player: MediaPlayer? = null
    private var currentTrackId: Long? = null
    private var isPrepared = false

    companion object {
        private const val PICK_AUDIO = 410
        private const val BACKGROUND = 0xFFF7F5FB.toInt()
        private const val CARD = 0xFFFFFFFF.toInt()
        private const val ACCENT = 0xFF6D5BD0.toInt()
        private const val TEXT = 0xFF24212D.toInt()
        private const val MUTED = 0xFF777180.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = LibraryDb(this)
        buildScreen()
        refreshLibrary()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }

        val title = TextView(this).apply {
            text = "musical"
            textSize = 32f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(TEXT)
        }
        page.addView(title)

        val subtitle = TextView(this).apply {
            text = "Твоя музыка — в одном месте"
            textSize = 14f
            setTextColor(MUTED)
            setPadding(0, 0, 0, dp(16))
        }
        page.addView(subtitle)

        page.addView(makeButton("＋  Добавить песню", ACCENT) { openAudioPicker() })

        val playlistTitle = TextView(this).apply {
            text = "ПЛЕЙЛИСТЫ"
            textSize = 11f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MUTED)
            setPadding(0, dp(22), 0, dp(7))
        }
        page.addView(playlistTitle)

        val playlistRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playlistSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        playlistRow.addView(playlistSpinner)
        playlistRow.addView(
            makeButton("＋", 0xFFEAE6F8.toInt(), TEXT) { promptAddPlaylist() }.apply {
                contentDescription = "Создать плейлист"
            }
        )
        page.addView(playlistRow)

        val authorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(12))
        }
        authorRow.addView(
            makeButton("Авторы", 0xFFEAE6F8.toInt(), TEXT) { showAuthors() }
        )
        authorRow.addView(
            TextView(this).apply {
                text = "  Авторы сохраняются в библиотеке"
                textSize = 12f
                setTextColor(MUTED)
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        page.addView(authorRow)

        countLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(4), 0, dp(10))
        }
        page.addView(countLabel)

        trackList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(trackList)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(scrollContent)
        }
        page.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        setContentView(page)

        playlistSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (isRefreshingSpinner) return
                selectedPlaylistId = if (position == 0) null else playlists.getOrNull(position - 1)?.id
                renderTracks()
            }
        }
    }

    private fun refreshLibrary() {
        playlists = database.listPlaylists()
        val labels = listOf("Вся библиотека") + playlists.map { it.name }
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        isRefreshingSpinner = true
        playlistSpinner.adapter = adapter
        val selectedIndex = if (selectedPlaylistId == null) {
            0
        } else {
            (playlists.indexOfFirst { it.id == selectedPlaylistId } + 1).coerceAtLeast(0)
        }
        playlistSpinner.setSelection(selectedIndex, false)
        isRefreshingSpinner = false
        renderTracks()
    }

    private fun renderTracks() {
        if (!::trackList.isInitialized) return
        trackList.removeAllViews()
        val tracks = database.listTracks(selectedPlaylistId)
        countLabel.text = if (selectedPlaylistId == null) {
            "ПЕСНИ  ·  ${tracks.size}"
        } else {
            "В ЭТОМ ПЛЕЙЛИСТЕ  ·  ${tracks.size}"
        }

        if (tracks.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Здесь пока пусто.\nНажми «Добавить песню», чтобы выбрать аудиофайл на устройстве."
                textSize = 15f
                setTextColor(MUTED)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(42), dp(24), dp(42))
            }
            trackList.addView(empty)
            return
        }

        tracks.forEach { track ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(13), dp(15), dp(13))
                background = roundedBackground(CARD, 18)
            }
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            labels.addView(TextView(this).apply {
                text = track.title
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
            })
            labels.addView(TextView(this).apply {
                text = track.author
                textSize = 13f
                setTextColor(MUTED)
                setPadding(0, dp(3), 0, 0)
            })
            top.addView(labels)
            top.addView(makeButton("▶", ACCENT) { playTrack(track) }.apply {
                contentDescription = "Воспроизвести ${track.title}"
            })
            card.addView(top)

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            actions.addView(makeButton("Текст песни", 0xFFEAE6F8.toInt(), TEXT) {
                showLyrics(track)
            })
            actions.addView(makeButton("В плейлист  ＋", 0xFFEAE6F8.toInt(), TEXT) {
                choosePlaylistForTrack(track.id)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply {
                    leftMargin = dp(7)
                }
            })
            card.addView(actions)

            trackList.addView(card, cardParams)
        }
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, PICK_AUDIO)
        } catch (_: Exception) {
            toast("Не удалось открыть выбор файлов")
        }
    }

    @Deprecated("Uses the platform document picker to keep persistent URI permission.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            toast("Не удалось сохранить доступ к исходному файлу. Выбери его через системное хранилище Android.")
            return
        }
        val fileName = displayName(uri)
        val suggestedTitle = fileName.substringBeforeLast('.', fileName).ifBlank { "Новая песня" }
        promptTrackDetails(uri, suggestedTitle)
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "Новая песня"
        } catch (_: Exception) {
            "Новая песня"
        }
    }

    private fun promptTrackDetails(uri: Uri, suggestedTitle: String) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val titleInput = makeInput("Название песни").apply { setText(suggestedTitle) }
        val authorInput = makeInput("Автор / исполнитель")
        val lyricsInput = makeInput("Текст песни (необязательно)").apply {
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP
        }
        form.addView(titleInput)
        form.addView(authorInput)
        form.addView(lyricsInput)

        AlertDialog.Builder(this)
            .setTitle("Добавить песню")
            .setView(form)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val id = database.addTrack(
                    titleInput.text.toString(),
                    authorInput.text.toString(),
                    uri.toString(),
                    lyricsInput.text.toString()
                )
                if (id > 0) {
                    selectedPlaylistId = null
                    refreshLibrary()
                    toast("Песня добавлена. Аудиофайл не копировался.")
                } else {
                    toast("Не удалось сохранить песню")
                }
            }
            .show()
    }

    private fun promptAddPlaylist() {
        val input = makeInput("Название плейлиста")
        AlertDialog.Builder(this)
            .setTitle("Новый плейлист")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Создать") { _, _ ->
                val name = input.text.toString()
                if (database.addPlaylist(name)) {
                    refreshLibrary()
                    val newPlaylist = playlists.firstOrNull { it.name.equals(name.trim(), true) }
                    selectedPlaylistId = newPlaylist?.id
                    refreshLibrary()
                    toast("Плейлист создан")
                } else {
                    toast("Введите название или выберите другое: такой плейлист уже есть")
                }
            }
            .show()
    }

    private fun choosePlaylistForTrack(trackId: Long) {
        val options = database.listPlaylists()
        if (options.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("Сначала создай плейлист.")
                .setPositiveButton("Создать") { _, _ -> promptAddPlaylist() }
                .setNegativeButton("Закрыть", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Добавить в плейлист")
            .setItems(options.map { it.name }.toTypedArray()) { _, which ->
                val added = database.addTrackToPlaylist(options[which].id, trackId)
                toast(if (added) "Добавлено в плейлист" else "Эта песня уже есть в плейлисте")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAuthors() {
        val authors = database.listAuthors()
        val message = if (authors.isEmpty()) {
            "Авторов пока нет. Они появятся после добавления песен или их можно добавить вручную."
        } else {
            authors.joinToString("\n")
        }
        AlertDialog.Builder(this)
            .setTitle("Авторы")
            .setMessage(message)
            .setPositiveButton("Добавить автора") { _, _ -> promptAddAuthor() }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun promptAddAuthor() {
        val input = makeInput("Имя автора")
        AlertDialog.Builder(this)
            .setTitle("Новый автор")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val added = database.addAuthor(input.text.toString())
                toast(if (added) "Автор добавлен" else "Введите имя или автор уже существует")
            }
            .show()
    }

    private fun showLyrics(track: Track) {
        val body = TextView(this).apply {
            text = track.lyrics.ifBlank { "Текст песни ещё не добавлен." }
            textSize = 16f
            setTextColor(TEXT)
            setPadding(dp(22), dp(8), dp(22), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this)
            .setTitle("${track.title} — ${track.author}")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun playTrack(track: Track) {
        if (currentTrackId == track.id && player != null && isPrepared) {
            val activePlayer = player ?: return
            if (activePlayer.isPlaying) {
                activePlayer.pause()
                toast("Пауза")
            } else {
                activePlayer.start()
                toast("Воспроизведение")
            }
            return
        }

        releasePlayer()
        try {
            val newPlayer = MediaPlayer()
            player = newPlayer
            currentTrackId = track.id
            isPrepared = false

            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            newPlayer.setDataSource(this, Uri.parse(track.uri))
            newPlayer.setOnPreparedListener { readyPlayer ->
                isPrepared = true
                readyPlayer.start()
                toast("Сейчас играет: ${track.title}")
            }
            newPlayer.setOnCompletionListener {
                releasePlayer()
                toast("Воспроизведение завершено")
            }
            newPlayer.setOnErrorListener { _, _, _ ->
                releasePlayer()
                toast("Не удалось воспроизвести файл. Возможно, оригинал перемещён или удалён.")
                true
            }
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
            toast("Не удалось открыть аудиофайл. Проверь, доступен ли оригинал.")
        }
    }

    private fun releasePlayer() {
        try {
            player?.reset()
            player?.release()
        } catch (_: Exception) {
            // The player may already be released after a media error.
        }
        player = null
        currentTrackId = null
        isPrepared = false
    }

    private fun makeInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
    }

    private fun makeButton(
        label: String,
        backgroundColor: Int,
        textColor: Int = Color.WHITE,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(textColor)
            background = roundedBackground(backgroundColor, 14)
            setPadding(dp(12), dp(3), dp(12), dp(3))
            minHeight = dp(42)
            minWidth = dp(42)
            setOnClickListener { action() }
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
