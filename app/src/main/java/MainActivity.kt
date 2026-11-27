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
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.MotionEvent
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
    private var wordWrap = true
    private var showLineNumbers = false
    private var textEncoding = "UTF-8"
    private lateinit var lineNumberView: LineNumberView
    private lateinit var scrollHandle: ScrollHandleView
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
        wordWrap = preferences.getBoolean("word_wrap", true)
        showLineNumbers = preferences.getBoolean("line_numbers", false)
        textEncoding = preferences.getString("encoding", "UTF-8") ?: "UTF-8"
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
        header.addView(toolButton("SETUP") { showSettingsDialog() }, LinearLayout.LayoutParams(dp(112), dp(56)))
        page.addView(header)

        fileLabel = TextView(this).apply {
            text = currentName
            textSize = 12f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(8), dp(3), dp(8), dp(5))
            maxLines = 1
        }
        page.addView(fileLabel)

        // Fixed line-range copy controls remain visible above the editor.
        val rangeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val rangeStart = EditText(this).apply { hint = "From line"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; textSize = 12f; setSingleLine(true) }
        val rangeEnd = EditText(this).apply { hint = "To line"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; textSize = 12f; setSingleLine(true) }
        rangeRow.addView(rangeStart, LinearLayout.LayoutParams(0, dp(42), 1f))
        rangeRow.addView(rangeEnd, LinearLayout.LayoutParams(0, dp(42), 1f))
        rangeRow.addView(toolButton("Copy range") {
            val first = rangeStart.text.toString().toIntOrNull()
            val last = rangeEnd.text.toString().toIntOrNull()
            copyLineRange(first, last)
        }, LinearLayout.LayoutParams(dp(112), dp(48)))
        page.addView(rangeRow)

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

        editor = EmojiEditText(this).apply {
            setTextColor(textColorFor(editorBackgroundColor))
            setHintTextColor(0xFF888888.toInt())
            hint = "Write or open a text/code file..."
            textSize = editorTextSize
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(editorBackgroundColor)
            setHorizontallyScrolling(!wordWrap)
            isSingleLine = false
            includeFontPadding = true
            setTextIsSelectable(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    dirty = true
                    updateStatus("Modified")
                    if (::lineNumberView.isInitialized) lineNumberView.invalidate()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        lineNumberView = LineNumberView().apply { visibility = if (showLineNumbers) View.VISIBLE else View.GONE }
        val editorContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(lineNumberView, LinearLayout.LayoutParams(dp(42), -1))
            addView(editor, LinearLayout.LayoutParams(-1, -2, 1f))
        }
        val editorScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(editorContent, FrameLayout.LayoutParams(-1, -2))
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                lineNumberView.scrollTo(0, scrollY)
                if (::scrollHandle.isInitialized) scrollHandle.invalidate()
            }
        }
        // Permanent draggable scrollbar handle over the editor's right edge.
        scrollHandle = ScrollHandleView().apply { bind(editorScroll) }
        val editorFrame = FrameLayout(this).apply {
            addView(editorScroll, FrameLayout.LayoutParams(-1, -1))
            addView(scrollHandle, FrameLayout.LayoutParams(dp(30), -1, Gravity.END or Gravity.TOP))
        }
        page.addView(editorFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(7), dp(5), dp(7), dp(5))
        }
        page.addView(status)
        setContentView(page)
    }

    private fun setEditorBackground(color: Int) {
        editorBackgroundColor = color
        if (::editor.isInitialized) {
            editor.setBackgroundColor(color)
            editor.setTextColor(textColorFor(color))
        }
        preferences.edit().putInt("editor_background", color).apply()
    }

    private fun showColorSetup(parent: LinearLayout) {
        val label = TextView(this).apply { text = "Editor background"; textSize = 13f }
        parent.addView(label)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val white = Button(this).apply { text = "White"; isAllCaps = false; setOnClickListener { setEditorBackground(Color.WHITE) } }
        row.addView(white, LinearLayout.LayoutParams(dp(76), dp(48)))
        val spectrum = HueColorPicker().apply { setOnColorChanged { setEditorBackground(it) } }
        row.addView(spectrum, LinearLayout.LayoutParams(0, dp(28), 1f))
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(54)))
    }

    private fun textColorFor(background: Int): Int {
        val r = Color.red(background)
        val g = Color.green(background)
        val b = Color.blue(background)
        val brightness = (r * 299 + g * 587 + b * 114) / 1000
        return if (brightness > 150) Color.BLACK else 0xFFF2F2F2.toInt()
    }

    private fun showSettingsDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val sizeLabel = TextView(this).apply { text = "Text size: ${editorTextSize.roundToInt()} sp"; textSize = 16f; gravity = Gravity.CENTER }
        box.addView(sizeLabel, LinearLayout.LayoutParams(-1, dp(38)))
        val sizeBar = SeekBar(this).apply { max = 42; progress = editorTextSize.roundToInt() - 10 }
        box.addView(sizeBar, LinearLayout.LayoutParams(-1, dp(48)))
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { editorTextSize = (progress + 10).toFloat(); sizeLabel.text = "Text size: ${editorTextSize.roundToInt()} sp"; editor.textSize = editorTextSize; if (::lineNumberView.isInitialized) lineNumberView.invalidate() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val wrapCheck = CheckBox(this).apply { text = "Word wrap"; isChecked = wordWrap }
        val linesCheck = CheckBox(this).apply { text = "Show line numbers"; isChecked = showLineNumbers }
        box.addView(wrapCheck); box.addView(linesCheck)
        val encodingLabel = TextView(this).apply { text = "Unicode / text encoding"; textSize = 13f }
        box.addView(encodingLabel)
        val encodingSpinner = Spinner(this)
        val encodings = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE")
        encodingSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, encodings)
        encodingSpinner.setSelection(encodings.indexOf(textEncoding).coerceAtLeast(0))
        box.addView(encodingSpinner)
        showColorSetup(box)
        AlertDialog.Builder(this).setTitle("Setup").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                wordWrap = wrapCheck.isChecked; showLineNumbers = linesCheck.isChecked; textEncoding = encodings[encodingSpinner.selectedItemPosition]
                editor.setHorizontallyScrolling(!wordWrap)
                lineNumberView.visibility = if (showLineNumbers) View.VISIBLE else View.GONE
                preferences.edit().putFloat("editor_text_size", editorTextSize).putBoolean("word_wrap", wordWrap).putBoolean("line_numbers", showLineNumbers).putString("encoding", textEncoding).apply()
                toast("Setup saved")
            }.show()
    }

    private fun toolButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            minHeight = dp(88)
            minimumHeight = dp(88)
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }
        }

    private fun buttonParams() = LinearLayout.LayoutParams(0, dp(88), 1f).apply {
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

        val charset = java.nio.charset.Charset.forName(textEncoding)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()

        editor.setText(text)
        editor.setSelection(0)
        currentUri = uri
        currentName = queryDisplayName(uri) ?: "Opened file"
        fileLabel.text = currentName
        dirty = false
        updateStatus("Opened $textEncoding | ${lineCount()} lines")
    }

    private fun writeDocument(uri: Uri) {
        val bytes = editor.text.toString().toByteArray(java.nio.charset.Charset.forName(textEncoding))
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IllegalStateException("Cannot write file")
        currentUri = uri
        currentName = queryDisplayName(uri) ?: currentName
        fileLabel.text = currentName
        dirty = false
        updateStatus("Saved $textEncoding | ${lineCount()} lines")
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

    private fun copyLineRange(first: Int?, last: Int?) {
        if (first == null || last == null || first < 1 || last < first) { toast("Enter a valid line range"); return }
        val lines = editor.text.toString().split("\n")
        if (last > lines.size) { toast("End line exceeds ${lines.size}"); return }
        val start = lines.take(first - 1).sumOf { it.length + 1 }
        val end = start + lines.subList(first - 1, last).sumOf { it.length } + (last - first)
        editor.requestFocus(); editor.setSelection(start.coerceAtMost(editor.length()), end.coerceAtMost(editor.length()))
        val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
         clip.setPrimaryClip(android.content.ClipData.newPlainText("STE lines $first-$last", editor.text.subSequence(start, end)))
        toast("Lines $first-$last copied")
    }

    private inner class LineNumberView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(12).toFloat(); color = 0xFF888888.toInt() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!::editor.isInitialized) return
            val layout = editor.layout ?: return
            val firstVisible = layout.getLineForVertical(editor.scrollY)
            val lastVisible = layout.getLineForVertical(editor.scrollY + editor.height)
            for (line in firstVisible..lastVisible.coerceAtMost(layout.lineCount - 1)) {
                val y = layout.getLineBaseline(line) - scrollY
                canvas.drawText((line + 1).toString(), dp(4).toFloat(), y.toFloat(), paint)
            }
        }
    }

    /** Always-visible scrollbar: touch or drag its thumb to navigate the document. */
    private inner class ScrollHandleView : View(this@MainActivity) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000.toInt() }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBDBDBD.toInt() }
        private var scrollView: ScrollView? = null
        private var dragging = false
        private var thumbTop = 0f
        private var thumbHeight = dp(64).toFloat()
        private val inset = dp(8).toFloat()

        fun bind(view: ScrollView) {
            scrollView = view
            isClickable = true
            contentDescription = "Drag to scroll through the document"
        }

        private fun updateThumb() {
            val sv = scrollView ?: return
            val range = (sv.getChildAt(0)?.height ?: 0) - sv.height
            if (range <= 0 || height <= 0) {
                thumbHeight = height.toFloat()
                thumbTop = 0f
                return
            }
            thumbHeight = (height.toFloat() * sv.height / (sv.height + range))
                .coerceAtLeast(dp(52).toFloat()).coerceAtMost(height.toFloat())
            val travel = (height - thumbHeight).coerceAtLeast(1f)
            thumbTop = travel * (sv.scrollY.toFloat() / range).coerceIn(0f, 1f)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val sv = scrollView ?: return
            val range = (sv.getChildAt(0)?.height ?: 0) - sv.height
            if (range <= 0) return
            updateThumb()
            val centerX = width - inset
            canvas.drawRoundRect(centerX - dp(5), 0f, centerX + dp(1), height.toFloat(), dp(5).toFloat(), dp(5).toFloat(), trackPaint)
            canvas.drawRoundRect(centerX - dp(7), thumbTop, centerX + dp(3), thumbTop + thumbHeight, dp(6).toFloat(), dp(6).toFloat(), thumbPaint)
        }

        private fun scrollToTouch(y: Float) {
            val sv = scrollView ?: return
            val range = (sv.getChildAt(0)?.height ?: 0) - sv.height
            if (range <= 0) return
            updateThumb()
            val travel = (height - thumbHeight).coerceAtLeast(1f)
            val top = (y - thumbHeight / 2f).coerceIn(0f, travel)
            sv.scrollTo(0, (top / travel * range).toInt())
            thumbTop = top
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    scrollToTouch(event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) scrollToTouch(event.y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private inner class HueColorPicker : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val spectrum = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
        private var callback: ((Int) -> Unit)? = null
        fun setOnColorChanged(action: (Int) -> Unit) { callback = action }
        override fun onDraw(canvas: Canvas) {
            val shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, spectrum, null, android.graphics.Shader.TileMode.CLAMP)
            paint.shader = shader
            canvas.drawRoundRect(0f, 2f, width.toFloat(), height - 2f, height / 2f, height / 2f, paint)
            paint.shader = null
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP) {
                val hsv = floatArrayOf((event.x / width.coerceAtLeast(1) * 360f).coerceIn(0f, 359f), 1f, 1f)
                callback?.invoke(Color.HSVToColor(hsv)); return true
            }
            return true
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
