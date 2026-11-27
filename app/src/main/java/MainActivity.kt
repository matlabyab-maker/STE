package com.ste.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.widget.EmojiEditText
import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var editor: EmojiEditText
    private lateinit var status: TextView
    private lateinit var fileLabel: TextView
    private lateinit var colorRow: LinearLayout
    private var currentUri: Uri? = null
    private var currentName = "Untitled"
    private var dirty = false
    private var lastSearch = ""
    private var searchFrom = 0
    private var editorTextSize = 16f
    private var editorBackgroundColor = 0xFF151515.toInt()

    private val preferences by lazy { getSharedPreferences("ste_preferences", MODE_PRIVATE) }

    companion object {
        private const val OPEN_FILE = 1101
        private const val SAVE_FILE = 1102
        private var emojiInitialized = false
        private val EDITOR_COLORS = intArrayOf(
            0xFF151515.toInt(), 0xFF242424.toInt(), 0xFF172235.toInt(),
            0xFF263238.toInt(), 0xFFF5F5F5.toInt(), 0xFFFFF3E0.toInt(),
            0xFFE8F5E9.toInt(), 0xFFE3F2FD.toInt(), 0xFFFFF9C4.toInt(),
            0xFFFCE4EC.toInt(), 0xFFEDE7F6.toInt()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorTextSize = preferences.getFloat("editor_text_size", 16f).coerceIn(10f, 36f)
        editorBackgroundColor = preferences.getInt("editor_background", 0xFF151515.toInt())
        initializeEmojiSupport()
        buildUi()
        updateStatus("Ready")
        // Load the bundled emoji font after the first screen has been drawn.
        editor.post {
            try {
                EmojiCompat.get().load()
            } catch (_: IllegalStateException) {
                toast("Emoji font could not be initialized")
            }
        }
    }

    private fun initializeEmojiSupport() {
        if (emojiInitialized) return
        val config = BundledEmojiCompatConfig(
            applicationContext,
            Executors.newSingleThreadExecutor()
        ).setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
        EmojiCompat.init(config)
        emojiInitialized = true
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(5), dp(7), dp(5))
            setBackgroundColor(0xFF202124.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "STE"
            textSize = 22f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(7), 0, dp(7), 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(toolButton("Settings") { showSettingsDialog() }, LinearLayout.LayoutParams(-2, dp(40)))
        page.addView(header)

        fileLabel = TextView(this).apply {
            text = currentName
            textSize = 12f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(8), dp(3), dp(8), dp(5))
            maxLines = 1
        }
        page.addView(fileLabel)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools.addView(toolButton("Open") { openFile() }, buttonParams())
        tools.addView(toolButton("Save") { saveFile() }, buttonParams())
        tools.addView(toolButton("Find") { showFindDialog(false) }, buttonParams())
        tools.addView(toolButton("Replace") { showFindDialog(true) }, buttonParams())
        page.addView(tools)

        val editTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        editTools.addView(toolButton("Undo") { toast("Undo is not available in this starter version") }, buttonParams())
        editTools.addView(toolButton("Select all") { editor.selectAll() }, buttonParams())
        editTools.addView(toolButton("Clear") { editor.text.clear() }, buttonParams())
        page.addView(editTools)

        val colorLabel = TextView(this).apply {
            text = "Editor background"
            textSize = 11f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(7), dp(5), 0, 0)
        }
        page.addView(colorLabel)
        val paletteScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
        }
        colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        paletteScroll.addView(colorRow)
        page.addView(paletteScroll, LinearLayout.LayoutParams(-1, dp(48)))
        rebuildColorPalette()

        editor = EmojiEditText(this).apply {
            setTextColor(textColorFor(editorBackgroundColor))
            setHintTextColor(0xFF888888.toInt())
            hint = "Write or open a text/code file..."
            textSize = editorTextSize
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(editorBackgroundColor)
            setHorizontallyScrolling(false)
            isSingleLine = false
            includeFontPadding = true
            setTextIsSelectable(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    dirty = true
                    updateStatus("Modified")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        val editorScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(editor, FrameLayout.LayoutParams(-1, -2))
        }
        page.addView(editorScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(7), dp(5), dp(7), dp(5))
        }
        page.addView(status)
        setContentView(page)
    }

    private fun rebuildColorPalette() {
        if (!::colorRow.isInitialized) return
        colorRow.removeAllViews()
        EDITOR_COLORS.forEach { color ->
            val selected = color == editorBackgroundColor
            val swatch = TextView(this).apply {
                contentDescription = "Background color ${String.format("#%06X", 0xFFFFFF and color)}"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(if (selected) 3 else 1), if (selected) 0xFFFFC107.toInt() else 0xFF777777.toInt())
                }
                setOnClickListener { setEditorBackground(color) }
            }
            colorRow.addView(swatch, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
    }

    private fun setEditorBackground(color: Int) {
        editorBackgroundColor = color
        if (::editor.isInitialized) {
            editor.setBackgroundColor(color)
            editor.setTextColor(textColorFor(color))
        }
        preferences.edit().putInt("editor_background", color).apply()
        rebuildColorPalette()
    }

    private fun textColorFor(background: Int): Int {
        val r = Color.red(background)
        val g = Color.green(background)
        val b = Color.blue(background)
        val brightness = (r * 299 + g * 587 + b * 114) / 1000
        return if (brightness > 150) Color.BLACK else 0xFFF2F2F2.toInt()
    }

    private fun showSettingsDialog() {
        val originalSize = editorTextSize
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val sizeLabel = TextView(this).apply {
            text = "Text size: ${editorTextSize.roundToInt()} sp"
            textSize = 16f
            setTextColor(0xFF202124.toInt())
            gravity = Gravity.CENTER
        }
        box.addView(sizeLabel, LinearLayout.LayoutParams(-1, dp(38)))
        val sizeBar = SeekBar(this).apply {
            max = 26
            progress = editorTextSize.roundToInt() - 10
        }
        box.addView(sizeBar, LinearLayout.LayoutParams(-1, dp(48)))
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editorTextSize = (progress + 10).toFloat()
                sizeLabel.text = "Text size: ${editorTextSize.roundToInt()} sp"
                editor.textSize = editorTextSize
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(box)
            .setNegativeButton("Cancel") { _, _ ->
                editorTextSize = originalSize
                editor.textSize = originalSize
            }
            .setPositiveButton("Save") { _, _ ->
                preferences.edit().putFloat("editor_text_size", editorTextSize).apply()
                toast("Text size saved")
            }
            .show()
    }

    private fun toolButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            minHeight = dp(42)
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }
        }

    private fun buttonParams() = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
        marginEnd = dp(2)
    }

    private fun openFile() {
        if (dirty) {
            AlertDialog.Builder(this)
                .setMessage("There are unsaved changes. Open another file?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ -> launchOpen() }
                .show()
        } else launchOpen()
    }

    private fun launchOpen() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, OPEN_FILE)
    }

    private fun saveFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, safeFileName(currentName))
        }
        startActivityForResult(intent, SAVE_FILE)
    }

    private fun safeFileName(name: String): String =
        if (name.contains('.')) name else "$name.txt"

    @Deprecated("Uses document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                OPEN_FILE -> readDocument(uri)
                SAVE_FILE -> writeDocument(uri)
            }
        } catch (e: Exception) {
            toast("File error: ${e.message ?: "Unknown error"}")
            updateStatus("File operation failed")
        }
    }

    private fun readDocument(uri: Uri) {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: throw IllegalStateException("Cannot read file")

        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()

        editor.setText(text)
        editor.setSelection(0)
        currentUri = uri
        currentName = queryDisplayName(uri) ?: "Opened file"
        fileLabel.text = currentName
        dirty = false
        updateStatus("Opened UTF-8 | ${lineCount()} lines")
    }

    private fun writeDocument(uri: Uri) {
        val bytes = editor.text.toString().toByteArray(StandardCharsets.UTF_8)
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IllegalStateException("Cannot write file")
        currentUri = uri
        currentName = queryDisplayName(uri) ?: currentName
        fileLabel.text = currentName
        dirty = false
        updateStatus("Saved UTF-8 | ${lineCount()} lines")
        toast("Saved")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    private fun showFindDialog(replace: Boolean) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }
        val findInput = EditText(this).apply { hint = "Find text" }
        box.addView(findInput)
        val replaceInput = if (replace) EditText(this).apply { hint = "Replace with" } else null
        if (replaceInput != null) box.addView(replaceInput)

        AlertDialog.Builder(this)
            .setTitle(if (replace) "Find and replace" else "Find")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Find next") { _, _ ->
                val query = findInput.text.toString()
                if (query.isNotEmpty()) findNext(query)
            }
            .setPositiveButton(if (replace) "Replace all" else "Find") { _, _ ->
                val query = findInput.text.toString()
                if (query.isNotEmpty()) {
                    if (replace) {
                        val replacement = replaceInput?.text?.toString() ?: ""
                        val source = editor.text.toString()
                        val count = Regex(Regex.escape(query)).findAll(source).count()
                        editor.setText(source.replace(query, replacement))
                        updateStatus("$count replacements")
                    } else findNext(query)
                       }
            }
            .show()
    }

    private fun findNext(query: String) {
        val content = editor.text.toString()
        if (lastSearch != query) searchFrom = 0
        lastSearch = query
        val found = content.indexOf(query, searchFrom, ignoreCase = false)
        val index = if (found >= 0) found else content.indexOf(query)
        if (index >= 0) {
            editor.requestFocus()
            editor.setSelection(index, index + query.length)
            searchFrom = index + query.length
            updateStatus("Found at position $index")
        } else {
            searchFrom = 0
            toast("Not found")
        }
    }

    private fun lineCount(): Int = editor.text.toString().count { it == '\n' } + 1

    private fun updateStatus(message: String) {
        if (::status.isInitialized) status.text = "$message   |   Lines: ${lineCount()}"
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (dirty) {
            AlertDialog.Builder(this)
                .setMessage("Discard unsaved changes?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Discard") { _, _ -> super.onBackPressed() }
                .show()
        } else super.onBackPressed()
    }
}             
