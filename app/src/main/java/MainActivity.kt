package com.ste.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import androidx.emoji2.widget.EmojiEditText
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class MainActivity : Activity() {
    private lateinit var editor: EmojiEditText
    private lateinit var status: TextView
    private lateinit var fileLabel: TextView
    private var currentUri: Uri? = null
    private var currentName = "Untitled"
    private var dirty = false
    private var lastSearch = ""
    private var searchFrom = 0

    companion object {
        private const val OPEN_FILE = 1101
        private const val SAVE_FILE = 1102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateStatus("Ready")
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(5), dp(7), dp(5))
            setBackgroundColor(0xFF202124.toInt())
        }

        val title = TextView(this).apply {
            text = "STE"
            textSize = 22f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(7), 0, dp(7), 0)
        }
        page.addView(title, LinearLayout.LayoutParams(-1, dp(42)))

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

        editor = EmojiEditText(this).apply {
            setTextColor(0xFFF2F2F2.toInt())
            setHintTextColor(0xFF888888.toInt())
            hint = "Write or open a text/code file..."
            textSize = 15f
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFF151515.toInt())
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

        // Decode strictly: malformed UTF-8 is reported instead of silently replaced.
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
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
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
        if (::status.isInitialized) {
            status.text = "$message   |   Lines: ${lineCount()}"
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
