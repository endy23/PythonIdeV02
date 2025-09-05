package com.endyaris.pythonidev02

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.amrdeveloper.codeview.CodeView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.*
import java.util.regex.Pattern
import android.view.inputmethod.InputMethodManager
import android.view.animation.TranslateAnimation
import androidx.core.view.isVisible
import androidx.appcompat.widget.SearchView
import android.text.style.BackgroundColorSpan
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder


class TestActivity : AppCompatActivity() {

    private lateinit var codeEditor: CodeView
    private lateinit var outputText: TextView
    private lateinit var outputScrollView: ScrollView
    private lateinit var keyboardBar: LinearLayout

    private val PREFS_NAME = "python_ide_prefs"
    private val CODE_KEY = "saved_code"

    private val undoStack: Deque<EditorState> = ArrayDeque()
    private val redoStack: Deque<EditorState> = ArrayDeque()
    private var isUndoOrRedo = false

    private var currentFileUri: Uri? = null
    private var pythonModules: List<String> = emptyList()
    private var autocompletePopup: PopupWindow? = null
    private lateinit var autocompleteAdapter: AutocompleteAdapter
    private var searchMatches: List<IntRange> = emptyList()
    private var currentSearchIndex: Int = -1
    private var matchPositions: List<IntRange> = emptyList()
    
    private var matchCount = 0
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        codeEditor = findViewById(R.id.codeEditor)
        outputText = findViewById(R.id.outputText)
        outputScrollView = findViewById(R.id.outputScrollView)
        keyboardBar = findViewById(R.id.keyboardBar)

        codeEditor.setEnableLineNumber(true)
        codeEditor.setLineNumberTextSize(50f)
        codeEditor.setLineNumberTextColor(Color.GREEN)
        codeEditor.setTabLength(4)
        codeEditor.setEnableAutoIndentation(true)

        setupKeyboardBarForCodeView()
        setupKeyboardVisibilityListener()
        setupSyntaxHighlighting()

        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        loadPythonModules()

        initAutocompletePopup()

        val prefs = getSharedPreferences("editor_prefs", MODE_PRIVATE)
        val lastFileUri = prefs.getString("last_file", null)
        if (lastFileUri != null) loadFile(Uri.parse(lastFileUri))

        val runButton = findViewById<Button>(R.id.runButton)
        runButton.setOnClickListener { runCode() }

        codeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUndoOrRedo) {
                    val cursorPos = codeEditor.selectionStart
                    undoStack.addLast(EditorState(s.toString(), cursorPos))
                    if (undoStack.size > 100) undoStack.removeFirst()
                    redoStack.clear()
                }

                val lineStart = codeEditor.text.lastIndexOf('\n', codeEditor.selectionStart - 1) + 1
                val lineText = codeEditor.text.substring(lineStart, codeEditor.selectionStart)
                val match = Regex("""^\s*import\s+([a-zA-Z0-9_]*)$""").find(lineText)
                match?.let {
                    val typed = it.groupValues[1]
                    showAutocompletePopupAtCursor(typed)
                } ?: autocompletePopup?.dismiss()
            }
        })
        
        setupColonAutoIndent()
        setupAutoPairing()
        
    
    }

    /** --------------------------- AUTOCOMPLETE --------------------------- **/

    private fun initAutocompletePopup() {
        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.WHITE)
        }

        autocompleteAdapter = AutocompleteAdapter(this, mutableListOf())
        listView.adapter = autocompleteAdapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = autocompleteAdapter.getItem(position)
            if (selected != null) {
                insertAutocompleteSuggestion(selected)
                autocompletePopup?.dismiss()
            }
        }

        autocompletePopup = PopupWindow(
            listView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            isFocusable = false // ⚡ Δεν κλείνει το πληκτρολόγιο
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun showAutocompletePopupAtCursor(prefix: String) {
        val suggestions = pythonModules.filter { it.startsWith(prefix) }
        if (suggestions.isNotEmpty()) {
            autocompleteAdapter.updateData(suggestions)

            val layout = codeEditor.layout ?: return
            val line = layout.getLineForOffset(codeEditor.selectionStart)
            val baseline = layout.getLineBaseline(line)
            val x = layout.getPrimaryHorizontal(codeEditor.selectionStart).toInt()
            val y = baseline + codeEditor.paddingTop

            if (!autocompletePopup!!.isShowing) {
                autocompletePopup!!.showAtLocation(
                    codeEditor,
                    Gravity.TOP or Gravity.START,
                    x + codeEditor.paddingLeft + 50,
                    y + codeEditor.paddingTop + 80
                )
            } else {
                autocompleteAdapter.notifyDataSetChanged()
            }
        } else {
            autocompletePopup?.dismiss()
        }
    }

    private fun insertAutocompleteSuggestion(suggestion: String) {
        val start = codeEditor.selectionStart
        val lineStart = codeEditor.text.lastIndexOf('\n', start - 1) + 1
        val lineText = codeEditor.text.substring(lineStart, start)

        val match = Regex("""^\s*import\s+([a-zA-Z0-9_]*)$""").find(lineText)
        match?.let {
            val typed = it.groupValues[1]
            codeEditor.text.replace(start - typed.length, start, suggestion)
            codeEditor.setSelection(lineStart + "import ".length + suggestion.length)
        }
    }
    
    private fun setupColonAutoIndent() {
    codeEditor.addTextChangedListener(object : TextWatcher {
        private var lastLength = 0

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            lastLength = s?.length ?: 0
        }

        override fun afterTextChanged(s: Editable?) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (s == null) return
            if (s.length > lastLength) {
                val inserted = s.subSequence(start, start + count).toString()
                if (inserted.contains(":")) {
                    // Insert newline + tab after the cursor
                    val cursor = codeEditor.selectionStart
                    codeEditor.text.insert(cursor, "\n\t")
                    codeEditor.setSelection(cursor + 2) // move cursor after the tab
                }
            }
        }
    })
}

    /** --------------------------- KEYBOARD BAR --------------------------- **/

   private fun setupKeyboardBarForCodeView() {
    val buttons = mapOf(
        R.id.btnTab to { insertTextWithCursor("    ") },
        R.id.btnQuote to { wrapSelectionWith("\"")  },       // "
        R.id.btnApostrophe to { wrapSelectionWith("'") },   // '
        R.id.btnOpenParen to { wrapSelectionWith("(", ")") },
        R.id.btnCloseParen to { insertTextWithCursor(")") },
        R.id.btnColon to { insertColonNewLine() },
        R.id.btnHash to {insertTextWithCursor("#")},
        R.id.btnAt to {insertTextWithCursor("@")},
        R.id.btnDollar to {insertTextWithCursor("$")}
    )

    buttons.forEach { (id, action) ->
        findViewById<Button>(id).setOnClickListener { action() }
    }
}

// ------------------ Wrap Selection ------------------
    private fun wrapSelectionWith(open: String, close: String = open) {
        val start = codeEditor.selectionStart
        val end = codeEditor.selectionEnd
        val text = codeEditor.text ?: return

        if (start != end) {
            val selected = text.subSequence(start, end).toString()
            text.replace(start, end, open + selected + close)
            codeEditor.setSelection(start + open.length, end + open.length)
        } else {
            text.insert(start, open + close)
            codeEditor.setSelection(start + open.length)
        }
    }

    // ------------------ Colon + New Line + Tab ------------------
    private fun insertColonNewLine() {
        val start = codeEditor.selectionStart
        val text = codeEditor.text ?: return
        text.insert(start, ":\n    ")
        codeEditor.setSelection(start + 5) // cursor μετά το tab
    }

// ------------------ Insert Text Helper ------------------
    private fun insertTextWithCursor(before: String, after: String = "") {
        val start = codeEditor.selectionStart
        val end = codeEditor.selectionEnd
        val text = codeEditor.text ?: return

        if (start != end && after.isNotEmpty()) {
            val selected = text.subSequence(start, end).toString()
            text.replace(start, end, before + selected + after)
            codeEditor.setSelection(start + before.length + selected.length + after.length)
        } else {
            text.replace(start, end, before + after)
            codeEditor.setSelection(start + before.length)
        }
    }

    private fun setupKeyboardVisibilityListener() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            keyboardBar.visibility = if (keypadHeight > 200) View.VISIBLE else View.GONE
           // outputText.visibility = if (keypadHeight > 200) View.GONE else View.VISIBLE
            if (keypadHeight > 200) {
                if (outputText.isVisible) slideDownAndHide(outputText)
                resizeOutput(0)
            } else {
                if (!outputText.isVisible) slideUpAndShow(outputText)
                resizeOutput(165)
            }
        
        }
    }
    
    
    private fun resizeOutput(heightDp: Int) {
        val params = outputScrollView.layoutParams
        val scale = resources.displayMetrics.density
        params.height = if (heightDp == 0) 0 else (heightDp * scale).toInt()
        outputScrollView.layoutParams = params
    }

    
   private fun setupAutoPairing() {
    val pairs = mapOf(
        "\"" to "\"",
        "'" to "'",
        "(" to ")",
        "{" to "}",
        "[" to "]"
    )

    codeEditor.addTextChangedListener(object : TextWatcher {
        private var lastLength = 0

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            lastLength = s?.length ?: 0
        }

        override fun afterTextChanged(s: Editable?) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (s == null || count == 0) return
            val inserted = s.subSequence(start, start + count).toString()
            val cursor = codeEditor.selectionStart

            // Auto newline + tab after :
            if (inserted == ":") {
                codeEditor.text.insert(cursor, "\n\t")
                codeEditor.setSelection(cursor + 2)
                return
            }

            // Auto-pairing
            if (pairs.containsKey(inserted)) {
                val closing = pairs[inserted] ?: return
                codeEditor.text.insert(cursor, closing)
                codeEditor.setSelection(cursor)
            }
        }
    })
}

    /** --------------------------- SYNTAX HIGHLIGHT --------------------------- **/

    private fun setupSyntaxHighlighting() {
        // Keywords
        codeEditor.addSyntaxPattern(
            Pattern.compile("\\b(print|def|class|import|from|as|if|else|elif|for|while|try|except|finally|return|True|False|None)\\b"),
            Color.RED
        )
        // Numbers
        codeEditor.addSyntaxPattern(Pattern.compile("\\b\\d+\\b"), Color.BLUE)
        // Comments
        codeEditor.addSyntaxPattern(Pattern.compile("#.*$", Pattern.MULTILINE), Color.GRAY)
        // Strings
        codeEditor.addSyntaxPattern(Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\".*?\"|'.*?'"), Color.GREEN)
    }
    
    private fun highlightSearch(query: String) {
        val text = codeEditor.text as Spannable

        // Remove old search highlights (but keep syntax!)
        val oldSpans = text.getSpans(0, text.length, BackgroundColorSpan::class.java)
        for (span in oldSpans) {
            text.removeSpan(span)
        }

        // Apply new highlights
        if (query.isNotEmpty()) {
            val matcher = Pattern.compile(Pattern.quote(query)).matcher(text)
            while (matcher.find()) {
                text.setSpan(
                    BackgroundColorSpan(Color.YELLOW),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    /** --------------------------- UNDO / REDO --------------------------- **/

    private fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = EditorState(codeEditor.text.toString(), codeEditor.selectionStart)
            redoStack.addLast(current)
            val state = undoStack.removeLast()
            isUndoOrRedo = true
            codeEditor.setText(state.text)
            codeEditor.setSelection(state.cursorPosition.coerceIn(0, state.text.length))
            isUndoOrRedo = false
        }
    }

    private fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = EditorState(codeEditor.text.toString(), codeEditor.selectionStart)
            undoStack.addLast(current)
            val state = redoStack.removeLast()
            isUndoOrRedo = true
            codeEditor.setText(state.text)
            codeEditor.setSelection(state.cursorPosition.coerceIn(0, state.text.length))
            isUndoOrRedo = false
        }
    }

    /** --------------------------- MENU --------------------------- **/

    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.test_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val actionView = searchItem.actionView ?: return true
        val searchView = actionView.findViewById<SearchView>(R.id.customSearchView)
        val counterView = actionView.findViewById<TextView>(R.id.searchResultCounter)

        var matches: List<IntRange> = emptyList()
        var currentIndex = 0

        fun updateCounter() {
            counterView.text =
                if (matches.isNotEmpty()) "${currentIndex + 1}/${matches.size}" else "0/0"
        }

        fun highlightMatch(index: Int) {
            val text = codeEditor.text
            if (text.isNullOrEmpty() || matches.isEmpty()) return

            // 🟢 Step 1: clear old search highlights but KEEP syntax spans
            val spans = text.getSpans(0, text.length, BackgroundColorSpan::class.java)
            for (span in spans) {
                text.removeSpan(span)
            }

            // 🟢 Step 2: highlight all matches (yellow)
            for (range in matches) {
                text.setSpan(
                    BackgroundColorSpan(Color.YELLOW),
                    range.first,
                    range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 🟢 Step 3: highlight current match (orange)
            val current = matches[index]
            text.setSpan(
                BackgroundColorSpan(Color.parseColor("#FFA500")),
                current.first,
                current.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 🟢 Move cursor to current match
            codeEditor.setSelection(current.first, current.last + 1)
            codeEditor.requestFocus()
        }

        val undoItem = menu.findItem(R.id.btnUndo)
        val redoItem = menu.findItem(R.id.btnRedo)
        val nextItem = menu.findItem(R.id.action_search_next)
        val prevItem = menu.findItem(R.id.action_search_prev)

        // default state
        nextItem.isVisible = false
        prevItem.isVisible = false
        undoItem.isVisible = true
        redoItem.isVisible = true
        matches = emptyList()
        counterView.text = "0/0"
        setupSyntaxHighlighting()
        

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                undoItem.isVisible = false
                redoItem.isVisible = false
                nextItem.isVisible = true
                prevItem.isVisible = true

                searchView.isIconified = false   // make sure it's expanded
                searchView.requestFocusFromTouch()

                searchView.post {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchView.findFocus(), InputMethodManager.SHOW_FORCED)
        
            }
            return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                undoItem.isVisible = true
                redoItem.isVisible = true
                nextItem.isVisible = false
                prevItem.isVisible = false
                matches = emptyList()
                counterView.text = "0/0"

                // 🟢 restore syntax highlighting when search is closed
                setupSyntaxHighlighting()

                // 🟢 clear query when search is closed
                searchView.setQuery("", false)
                searchView.clearFocus()

                return true
            }
        })

        searchView.queryHint = "Search in code..."
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    val text = codeEditor.text.toString()
                    val regex = Regex(Regex.escape(query))
                    matches = regex.findAll(text).map { it.range }.toList()
                    currentIndex = 0
                    updateCounter()

                    if (matches.isNotEmpty()) {
                        highlightMatch(currentIndex)

                        // 🟢 Move cursor and scroll to match
                        val firstMatch = matches[currentIndex]
                        codeEditor.setSelection(firstMatch.first, firstMatch.last + 1)
                        codeEditor.requestFocus()
                    } else {
                        setupSyntaxHighlighting() // no matches, restore syntax
                    }
                    return true
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
        nextItem.setOnMenuItemClickListener {
            if (matches.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % matches.size
                updateCounter()
                highlightMatch(currentIndex)

                val matchRange = matches[currentIndex]
                codeEditor.setSelection(matchRange.first, matchRange.last + 1)
                codeEditor.requestFocus()
            }
            true
        }

        prevItem.setOnMenuItemClickListener {
            if (matches.isNotEmpty()) {
                currentIndex = if (currentIndex - 1 < 0) matches.size - 1 else currentIndex - 1
                updateCounter()
                highlightMatch(currentIndex)
                
                val matchRange = matches[currentIndex]
                codeEditor.setSelection(matchRange.first, matchRange.last + 1)
                codeEditor.requestFocus()
            }
            true
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_install_module -> { showInstallDialog(); true }
            R.id.action_list_modules -> { listModules(); true }
            R.id.btnUndo -> { undo(); true }
            R.id.btnRedo -> { redo(); true }
            R.id.menu_open -> { openFile(); true }
            R.id.menu_save -> { saveFile(); true }
            R.id.menu_save_as -> { saveFileAs(); true }
            R.id.action_search_next -> { moveToMatch(currentSearchIndex + 1); true }
            R.id.action_search_prev -> { moveToMatch(currentSearchIndex - 1); true }
            R.id.openSora -> {startActivity(Intent(this@TestActivity, SoraActivity::class.java)); true}
            else -> super.onOptionsItemSelected(item)
        }
    }
    
   

    /** --------------------------- FILE HANDLING --------------------------- **/

    private fun openFile() { openFileLauncher.launch(arrayOf("text/x-python", "text/plain")) }
    private fun saveFile() { currentFileUri?.let { writeToFile(it) } ?: saveFileAs() }
    private fun saveFileAs() { createFileLauncher.launch("untitled.py") }

    private fun writeToFile(uri: Uri) {
        contentResolver.openOutputStream(uri)?.bufferedWriter().use { it?.write(codeEditor.text.toString()) }
        setLastFile(uri)
    }

    private fun loadFile(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            currentFileUri = uri
            contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                val text = reader?.readText() ?: ""
                codeEditor.setText(text)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadFile(it) }
    }

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-python")) { uri ->
        uri?.let { writeToFile(it) }
    }

    private fun setLastFile(uri: Uri) {
        currentFileUri = uri
        getSharedPreferences("editor_prefs", MODE_PRIVATE).edit().putString("last_file", uri.toString()).apply()
    }

    /** --------------------------- PYTHON MODULES --------------------------- **/

    private fun loadPythonModules() {
        Thread {
            try {
                val py = Python.getInstance()
                val installer = py.getModule("installer")
                val result = installer.callAttr("list_installed_packages")?.toString() ?: ""
                pythonModules = result.split("\n").filter { it.isNotBlank() }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun installPythonModule(moduleName: String) {
        outputText.text = ""
        Thread {
            try {
                val py = Python.getInstance()
                val installer = py.getModule("installer")
                val result = installer.callAttr("install_package", moduleName).toString()
                runOnUiThread { outputText.append(result) }
            } catch (e: Exception) { runOnUiThread { outputText.append("Error: ${e.message}") } }
        }.start()
    }

    private fun listModules() {
        val scrollView = ScrollView(this)
        val tv = TextView(this)
        tv.text = "Installed Python modules:\n"
        scrollView.addView(tv)

        Thread {
            try {
                val py = Python.getInstance()
                val installer = py.getModule("installer")
                val result = installer.callAttr("list_all_packages")?.toString() ?: "Error"
                runOnUiThread { tv.append("\n$result") }
            } catch (e: Exception) { runOnUiThread { tv.append("\nError: ${e.message}") } }
        }.start()

        AlertDialog.Builder(this)
            .setTitle("Python Modules")
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInstallDialog() {
        val input = EditText(this)
        input.hint = "Module name (e.g., requests)"
        AlertDialog.Builder(this)
            .setTitle("Install Python Module")
            .setView(input)
            .setPositiveButton("Install") { _, _ ->
                val moduleName = input.text.toString().trim()
                if (moduleName.isNotEmpty()) installPythonModule(moduleName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** --------------------------- RUN PYTHON --------------------------- **/

    private fun runCode() {
        closeKeyboard()
        outputText.text = ""
        val code = codeEditor.text.toString()
        Thread {
            try {
                val py = Python.getInstance()
                val runner = py.getModule("runner")
                val result = runner.callAttr("execute", code).toString()
                runOnUiThread {
                    outputText.append(result)
                    outputScrollView.post { outputScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) {
                runOnUiThread { outputText.append("Error: ${e.message}") }
            }
        }.start()
    }

    /** --------------------------- INSERT TEXT --------------------------- **/

    
    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(CODE_KEY, codeEditor.text.toString()).apply()
    }
    
     private fun closeKeyboard() {
        val view = findViewById<View>(android.R.id.content)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    // 🔽 Slide down (hide)
    private fun slideDownAndHide(view: View) {
        val animate = TranslateAnimation(0f, 0f, 0f, view.height.toFloat())
        animate.duration = 200
        view.startAnimation(animate)
        view.visibility = View.GONE
    }

    // 🔼 Slide up (show)
    private fun slideUpAndShow(view: View) {
        view.visibility = View.VISIBLE
        val animate = TranslateAnimation(0f, 0f, view.height.toFloat(), 0f)
        animate.duration = 200
        view.startAnimation(animate)
    }
  
    private fun clearHighlights() {
        codeEditor.setText(codeEditor.text.toString()) // reset plain
        searchMatches = emptyList()
        currentSearchIndex = -1
    }

    // Store matches globally in your Activity


    fun findAllMatches(query: String): List<IntRange> {
        val text = codeEditor.text.toString()
        val regex = Regex(Pattern.quote(query), RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.range }.toList()
    }

    fun highlightMatches() {
        val text = codeEditor.text
        val spannable = SpannableString(text)

        // highlight all matches yellow
        matchPositions.forEachIndexed { index, range ->
            val color = if (index == currentIndex) Color.parseColor("#FFA500") // orange
                        else Color.YELLOW

            spannable.setSpan(
                BackgroundColorSpan(color),
                range.first,
                range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        codeEditor.setText(spannable, TextView.BufferType.SPANNABLE)
    }

    fun moveToMatch(index: Int) {
        if (matchCount == 0) return

        // wrap around
        currentIndex = ((index % matchCount) + matchCount) % matchCount  

       // updateCounter()
        highlightMatches()

        // scroll cursor to the active match
        val range = matchPositions[currentIndex]
        codeEditor.setSelection(range.first, range.last + 1)
    }
}

data class EditorState(val text: String, val cursorPosition: Int)

