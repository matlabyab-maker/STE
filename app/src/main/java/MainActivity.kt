package com.ste.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Canvas
import android.view.View
import android.text.Spannable
import android.text.style.ForegroundColorSpan
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
import java.util.regex.Pattern
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var editor: EmojiEditText
    private lateinit var status: TextView
    private lateinit var fileLabel: TextView
    private lateinit var colorPicker: HueColorPicker
    private var applyingSyntax = false
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorTextSize = preferences.getFloat("editor_text_size", 16f).coerceIn(10f, 36f)
        editorBackgroundColor = preferences.getInt("editor_background", 0xFF151515.toInt())
        initializeEmojiSupport()
        buildUi()
        updateStatus("Ready")
        editor.post { applySyntaxHighlighting() }
    }

    private fun initializeEmojiSupport() {
        if (emojiInitialized) return
        val config = BundledEmojiCompatConfig(applicationContext, Executors.newSingleThreadExecutor())
            .setReplaceAll(true)
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
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(5), 0, dp(5), 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
        header.addView(toolButton("Settings") { showSettingsDialog() }, LinearLayout.LayoutParams(-2, dp(32)))
        page.addView(header)

        fileLabel = TextView(this).apply {
            text = currentName
            textSize = 11f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(5), 0, dp(5), dp(2))
            maxLines = 1
        }
        page.addView(fileLabel)

        // All editor commands share one compact row to maximize editing space.
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Open" to { openFile() }, "Save" to { saveFile() },
            "Find" to { showFindDialog(false) }, "Repl" to { showFindDialog(true) },
            "Undo" to { toast("Undo is not available yet") },
            "All" to { editor.selectAll() }, "Clear" to { editor.text.clear() }
        ).forEach { (label, action) ->
            tools.addView(toolButton(label, action), LinearLayout.LayoutParams(0, dp(34), 1f))
        }
        page.addView(tools)

        val colorLabel = TextView(this).apply {
            text = "Background color — drag to choose"
            textSize = 10f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(dp(5), dp(2), 0, 0)
        }
        page.addView(colorLabel)
        colorPicker = HueColorPicker(this).apply {
            setOnColorChanged { setEditorBackground(it) }
            setColor(editorBackgroundColor)
        }
        page.addView(colorPicker, LinearLayout.LayoutParams(-1, dp(24)))

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
                override fun afterTextChanged(s: Editable?) {
                    if (!applyingSyntax) applySyntaxHighlighting()
                }
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
        private fun applySyntaxHighlighting() {
        if (!::editor.isInitialized || applyingSyntax) return
        val editable = editor.text ?: return
        applyingSyntax = true
        try {
            editable.getSpans(0, editable.length, ForegroundColorSpan::class.java).forEach {
                editable.removeSpan(it)
            }
            val source = editable.toString()
            fun paint(pattern: String, color: Int, flags: Int = Pattern.MULTILINE) {
                val matcher = Pattern.compile(pattern, flags).matcher(source)
                while (matcher.find()) {
                    editable.setSpan(ForegroundColorSpan(color), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            val ext = currentName.substringAfterLast('.', "").lowercase()
            val codeFile = ext in setOf("kt", "java", "js", "html", "htm", "css", "json", "xml", "py", "sh", "gradle", "kts", "yaml", "yml", "toml")
            if (!codeFile) return
            val base = textColorFor(editorBackgroundColor)
            val comment = if ((Color.red(editorBackgroundColor) * 299 + Color.green(editorBackgroundColor) * 587 + Color.blue(editorBackgroundColor) * 114) / 1000 < 115) 0xFF80A080.toInt() else 0xFF39733F.toInt()
            val string = if ((Color.red(editorBackgroundColor) * 299 + Color.green(editorBackgroundColor) * 587 + Color.blue(editorBackgroundColor) * 114) / 1000 < 115) 0xFFFFC078.toInt() else 0xFF9A4B00.toInt()
            val keyword = if ((Color.red(editorBackgroundColor) * 299 + Color.green(editorBackgroundColor) * 587 + Color.blue(editorBackgroundColor) * 114) / 1000 < 115) 0xFF80BFFF.toInt() else 0xFF174EA6.toInt()
            val number = if ((Color.red(editorBackgroundColor) * 299 + Color.green(editorBackgroundColor) * 587 + Color.blue(editorBackgroundColor) * 114) / 1000 < 115) 0xFFD7A6FF.toInt() else 0xFF7B2CBF.toInt()
            val tag = if ((Color.red(editorBackgroundColor) * 299 + Color.green(editorBackgroundColor) * 587 + Color.blue(editorBackgroundColor) * 114) / 1000 < 115) 0xFFFF8FA3.toInt() else 0xFFB00040.toInt()
            // Set readable base color, then overlay token-specific colors.
            if (source.isNotEmpty()) editable.setSpan(ForegroundColorSpan(base), 0, source.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            paint("(?s)/\\*.*?\\*/|//[^\\n]*|#[^\\n]*", comment)
            paint("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`[^`]*`", string)
            paint("\\b(class|fun|val|var|if|else|when|for|while|return|import|package|public|private|protected|static|final|new|this|null|true|false|const|let|function|def|from|as|try|catch|throw|async|await|extends|implements)\\b", keyword, Pattern.MULTILINE or Pattern.CASE_INSENSITIVE)
            paint("\\b[0-9]+(?:\\.[0-9]+)?\\b", number)
            paint("</?[A-Za-z][^>]*>|\\b[A-Za-z_-]+(?=\\s*=)", tag)
        } finally {
            applyingSyntax = false
        }
    }

    private fun setEditorBackground(color: Int) {
        editorBackgroundColor = color
        if (::editor.isInitialized) {
            editor.setBackgroundColor(color)
            editor.setTextColor(textColorFor(color))
        }
        preferences.edit().putInt("editor_background", color).apply()
        if (::colorPicker.isInitialized) colorPicker.setColor(color)
        applySyntaxHighlighting()
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
            textSize = 9f
            minHeight = dp(30)
            minimumHeight = dp(30)
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { action() }
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
        applySyntaxHighlighting()
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
                        applySyntaxHighlighting()
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

    /** One continuous full-spectrum color strip; tap or drag to choose any hue. */
    private inner class HueColorPicker(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var selectedColor = editorBackgroundColor
        private var listener: ((Int) -> Unit)? = null
        private val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE,
            Color.MAGENTA, Color.RED
        )
        fun setOnColorChanged(callback: (Int) -> Unit) { listener = callback }
        fun setColor(color: Int) { selectedColor = color; invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, android.graphics.Shader.TileMode.CLAMP)
            paint.shader = shader
            val radius = height / 2f
            canvas.drawRoundRect(0f, 1f, width.toFloat(), height - 1f, radius, radius, paint)
            paint.shader = null
            val x = (Color.red(selectedColor) + Color.green(selectedColor) + Color.blue(selectedColor)).let {
                // Marker position follows hue, including gray colors at the center.
                val hsv = FloatArray(3)
                Color.colorToHSV(selectedColor, hsv)
                hsv[0] / 360f * width
            }.coerceIn(radius, width - radius)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.WHITE
            canvas.drawCircle(x, height / 2f, radius - dp(2), paint)
            paint.style = Paint.Style.FILL
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE || event.action == android.view.MotionEvent.ACTION_UP) {
                val hue = (event.x / width.coerceAtLeast(1) * 360f).coerceIn(0f, 359.9f)
                val hsv = floatArrayOf(hue, 1f, 1f)
                selectedColor = Color.HSVToColor(hsv)
                listener?.invoke(selectedColor)
                invalidate()
                return true
            }
            return true
        }
    }

}
