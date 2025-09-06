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
import org.eclipse.tm4e.core.registry.IThemeSource
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

data class EditorState(val text: String, val cursorPosition: Int)

class SoraActivity : AppCompatActivity() {

    private lateinit var editor: CodeEditor
    private lateinit var outputText: TextView
    private lateinit var outputScrollView: ScrollView
    private lateinit var keyboardBar: View


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
    }
    
    private fun closeKeyboard() {
        val view = findViewById<View>(android.R.id.content)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
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
    
    
    override fun onDestroy() {
        super.onDestroy()
        editor.release()
    }
    
}