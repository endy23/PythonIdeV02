package com.endyaris.pythonidev02

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.rosemoe.sora.widget.CodeEditor
import android.graphics.Typeface
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import android.widget.TextView
import android.widget.Button
import android.widget.ScrollView
import android.view.View
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.graphics.Rect
import androidx.core.view.isVisible
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import android.text.style.BackgroundColorSpan
import android.text.Spannable
import android.view.Menu
import android.view.MenuItem
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import java.util.Deque
import java.util.ArrayDeque
import android.text.SpannableString
import com.itsaky.androidide.logsender.LogSender
import android.util.Log


data class EditorState(val text: String, val cursor: CharPosition)


class SoraActivity : AppCompatActivity() {

    private lateinit var editor: CodeEditor
    private lateinit var outputText: TextView
    private lateinit var outputScrollView: ScrollView
    private lateinit var keyboardBar: View
    
    private val undoStack: Deque<EditorState> = ArrayDeque()
    private val redoStack: Deque<EditorState> = ArrayDeque()
    private var isUndoOrRedo = false
    
    private val PREFS_NAME = "python_ide_prefs"
    private val CODE_KEY = "saved_code_sora"
    
    private var currentFileUri: Uri? = null
    private var pythonModules: List<String> = emptyList()
    
    private var searchMatches: List<IntRange> = emptyList()
    private var currentSearchIndex: Int = -1
    private var matchPositions: List<IntRange> = emptyList()
    
    private var matchCount = 0
    private var currentIndex = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sora)
        
        editor = findViewById(R.id.editor)
        outputText = findViewById(R.id.outputText)
        outputScrollView = findViewById(R.id.outputScrollView)
        keyboardBar = findViewById(R.id.keyboardBar)
        
        setupEditor()
        setupKeyboardBarForCodeView()
        setupKeyboardVisibilityListener()
        
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        val runButton = findViewById<Button>(R.id.runButton)
        runButton.setOnClickListener { runCode() }
    }
    
    private fun setupEditor() {
        editor.setText("""def popo(i):
    for a in range(i + 1):
    	print(a,"-",i)
    
popo(11)""")
        editor.typefaceText = Typeface.MONOSPACE
        editor.nonPrintablePaintingFlags =
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
            CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
            CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION

        // 1) Let TextMate read from app assets
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(applicationContext.assets)
        )

        // 2) Load & activate a TextMate theme (no .themes[], no ambiguity)
        val themeName = "quietlight"             // textmate/quietlight.json in assets
        //val themeName = "dark"
        val themePath = "textmate/$themeName.json"
        ThemeRegistry.getInstance().loadTheme(
            ThemeModel(
                IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream(themePath),
                    themePath,
                    null
                ),
                themeName
            )
        )
        ThemeRegistry.getInstance().setTheme(themeName)

        // 3) Apply TextMate color scheme to the editor (resolves overload ambiguity)
        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

        // 4) Load grammars (do NOT instantiate GrammarDefinition)
        // Put this file in src/main/assets/textmate/languages.json (see example below)
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        val grammar = GrammarRegistry.getInstance().findGrammar("source.python")
        Toast.makeText(this, "Grammar loaded? ${grammar != null}", Toast.LENGTH_SHORT).show()

        // 5) Set a TextMate language by its scope name (adjust to your language)
        val language = TextMateLanguage.create("source.python", /*enableCompletion*/ true)
        editor.setEditorLanguage(language)
        println("Succesfull load the laguage!!")
        

        Log.i("SoraActivity", "Successful load the language!!")
        //editor.getComponent<EditorAutoCompletion>()
                    //.setEnabledAnimation(true)
    }

    private fun runCode() {
        closeKeyboard()
        outputText.text = ""
        val code = editor.text.toString()
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
       
        editor.setEditorLanguage(TextMateLanguage.create("source.python", true))
    }
    
    
    private fun setupKeyboardBarForCodeView() {
        val buttons = mapOf(
            R.id.btnTab to { insertOrWrapText("    ") },
            R.id.btnQuote to { insertOrWrapText("\"","\"")  },       // "
            R.id.btnApostrophe to { insertOrWrapText("'","'") },   // '
            R.id.btnOpenParen to { insertOrWrapText("(", ")") },
            R.id.btnCloseParen to { insertOrWrapText(")") },
            R.id.btnColon to { insertColonNewLine() },
            R.id.btnHash to {insertOrWrapText("#")},
            R.id.btnAt to {insertOrWrapText("@")},
            R.id.btnDollar to {insertOrWrapText("$")}
        )
        
        buttons.forEach { (id, action) ->
            findViewById<Button>(id).setOnClickListener { action() }
        }
    }
    
    private fun insertColonNewLine() {
        val cursor = editor.cursor
        val content = editor.text

        val line = cursor.leftLine
        val column = cursor.leftColumn

        // Insert ":<newline><indent>" at the current caret position
        content.insert(line, column, ":\n    ")

        // Move caret to next line, after indentation
        editor.setSelection(line + 1, 4, true)
    }
    
    private fun insertOrWrapText(prefix: String, suffix: String = "") {
        val cursor = editor.cursor
        val text = editor.text

        val startLine = cursor.leftLine
        val startCol = cursor.leftColumn
        val endLine = cursor.rightLine
        val endCol = cursor.rightColumn

        if (startLine != endLine || startCol != endCol) {
            // --- Case: some text is selected → wrap it ---
            val selected = text.subSequence(
                text.getCharIndex(startLine, startCol),
                text.getCharIndex(endLine, endCol)
            ).toString()

            // Replace selection with wrapped text
            text.replace(
                text.getCharIndex(startLine, startCol),
                text.getCharIndex(endLine, endCol),
                "$prefix$selected$suffix"
            )

            // Place caret after prefix (before selection content)
            editor.setSelection(startLine, startCol + prefix.length, true)
        } else {
            // --- Case: no selection → insert pair ---
            text.insert(startLine, startCol, "$prefix$suffix")

            // Place caret inside the inserted pair
            editor.setSelection(startLine, startCol + prefix.length, true)
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
    
    private fun resizeOutput(heightDp: Int) {
        val params = outputScrollView.layoutParams
        val scale = resources.displayMetrics.density
        params.height = if (heightDp == 0) 0 else (heightDp * scale).toInt()
        outputScrollView.layoutParams = params
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
            val spannable = editor.text as? Spannable ?: return
            if (spannable.isEmpty() || matches.isEmpty()) return

            // 🟢 Step 1: clear old search highlights but KEEP syntax spans
            val spans = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
            for (span in spans) {
                spannable.removeSpan(span)
            }

            // 🟢 Step 2: highlight all matches (yellow)
            for (range in matches) {
                spannable.setSpan(
                    BackgroundColorSpan(Color.YELLOW),
                    range.first,
                    range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 🟢 Step 3: highlight current match (orange)
            val current = matches[index]
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#FFA500")),
                current.first,
                current.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 🟢 Move cursor to current match
            //editor.setSelection(current.first, current.last + 1)
            safeSetSelection(editor, current.first, current.last + 1)
            editor.requestFocus()
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
            val text = editor.text.toString()
            matches = Regex(Regex.escape(query))
                .findAll(text)
                .map { it.range }
                .toList()
            currentIndex = 0
            updateCounter()

            if (matches.isNotEmpty()) {
                moveToMatch(currentIndex)
            }
            return true
        }
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean = false
})

// Next / Previous buttons
nextItem.setOnMenuItemClickListener {
    if (matches.isNotEmpty()) {
        currentIndex = (currentIndex + 1) % matches.size
        updateCounter()
        moveToMatch(currentIndex)
    }
    true
}

prevItem.setOnMenuItemClickListener {
    if (matches.isNotEmpty()) {
        currentIndex = if (currentIndex - 1 < 0) matches.size - 1 else currentIndex - 1
        updateCounter()
        moveToMatch(currentIndex)
    }
    true
}

// Centralized safe move function
fun moveToMatch(index: Int) {
    val matchRange = matches[index]

    // Convert offsets -> CharPosition
    val startPos = safeCharPosition(editor, offsetToCharPosition(editor.text, matchRange.first))
    val endPos = safeCharPosition(editor, offsetToCharPosition(editor.text, matchRange.last + 1))

    // Convert CharPosition -> absolute index
    val startIndex = safeCharIndex(editor.text, startPos.line, startPos.column)
    val endIndex = safeCharIndex(editor.text, endPos.line, endPos.column)

    // Set selection safely
    safeSetSelection(editor, startIndex, endIndex)
    editor.requestFocus()

    // Highlight match
    highlightMatch(index)
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
            R.id.openSora -> {startActivity(Intent(this@SoraActivity, MainActivity::class.java)); true}
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    
       

    /** --------------------------- FILE HANDLING --------------------------- **/

    private fun openFile() { openFileLauncher.launch(arrayOf("text/x-python", "text/plain")) }
    private fun saveFile() { currentFileUri?.let { writeToFile(it) } ?: saveFileAs() }
    private fun saveFileAs() { createFileLauncher.launch("untitled.py") }

    private fun writeToFile(uri: Uri) {
        contentResolver.openOutputStream(uri)?.bufferedWriter().use { it?.write(editor.text.toString()) }
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
                editor.setText(text)
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

    
    /** --------------------------- INSERT TEXT --------------------------- **/

    
    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(CODE_KEY, editor.text.toString()).apply()
    }
    
     private fun closeKeyboard() {
        val view = findViewById<View>(android.R.id.content)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
     /** --------------------------- UNDO / REDO --------------------------- **/

    private fun undo() {
    if (undoStack.isNotEmpty()) {
        val current = EditorState(editor.text.toString(), editor.cursor.left())
        redoStack.addLast(current)

        val state = undoStack.removeLast()
        isUndoOrRedo = true
        editor.setText(state.text)
        editor.setSelection(state.cursor.line, state.cursor.column) // ✅ use line & column
        isUndoOrRedo = false
    }
}

private fun redo() {
    if (redoStack.isNotEmpty()) {
        val current = EditorState(editor.text.toString(), editor.cursor.left())
        undoStack.addLast(current)

        val state = redoStack.removeLast()
        isUndoOrRedo = true
        editor.setText(state.text)
        editor.setSelection(state.cursor.line, state.cursor.column) // ✅ use line & column
        isUndoOrRedo = false
    }
}
    
    fun moveToMatch(index: Int) {
        if (matchCount == 0) return

        // wrap around
        currentIndex = ((index % matchCount) + matchCount) % matchCount  

       // updateCounter()
        highlightMatches()

        // scroll cursor to the active match
        val range = matchPositions[currentIndex]
        editor.setSelection(range.first, range.last + 1)
    }
    
    fun highlightMatches() {
        val text = editor.text
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
        editor.setText(spannable)
        editor.invalidate() // force redraw if needed
    }
    
    

    fun offsetToCharPosition(content: CharSequence, offset: Int): CharPosition {
        var line = 0
        var col = 0
        var count = 0

        for (i in content.indices) {
            if (count == offset) {
                return CharPosition(line, col)
            }
            if (content[i] == '\n') {
                line++
                col = 0
            } else {
                col++
            }
            count++
        }
        return CharPosition(line, col) // fallback at end
    }
    
    fun safeCharIndex(text: Content, line: Int, column: Int): Int {
        val maxLine = text.lineCount - 1
        val clampedLine = line.coerceIn(0, maxLine)
        val maxColumn = text.getLine(clampedLine).length
        val clampedColumn = column.coerceIn(0, maxColumn)
        return text.getCharIndex(clampedLine, clampedColumn)
    }
    
    fun safeCharPosition(editor: CodeEditor, pos: CharPosition): CharPosition {
    val line = pos.line.coerceIn(0, editor.text.lineCount - 1)
    val column = pos.column.coerceIn(0, editor.text.getLine(line).length)
    return CharPosition(line, column)
}

    fun safeSetSelection(editor: CodeEditor, start: Int, end: Int) {
    val content = editor.text
    val maxIndex = content.length // total characters

    val safeStart = start.coerceIn(0, maxIndex)
    val safeEnd = end.coerceIn(0, maxIndex)

    try {
        if (safeStart <= safeEnd) {
            editor.setSelection(safeStart, safeEnd)
        } else {
            editor.setSelection(safeEnd, safeStart)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.i("SoraActivity", e.printStackTrace().toString())
      Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()

    }
}
    
    override fun onDestroy() {
        super.onDestroy()
        editor.release()
    }
    
}