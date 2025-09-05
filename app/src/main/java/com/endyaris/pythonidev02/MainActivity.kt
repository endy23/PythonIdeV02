package com.endyaris.pythonidev02

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.eclipse.tm4e.core.registry.IGrammarSource
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var editor: CodeEditor
    private lateinit var themeRegistry: ThemeRegistry
    private lateinit var grammarRegistry: GrammarRegistry
    private lateinit var securePrefs: SecurePreferences
    private lateinit var api: PythonAnywhereApi
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var testButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        securePrefs = SecurePreferences(this)
        checkCredentials()
        
        editor = findViewById(R.id.editor)
        val runButton: Button = findViewById(R.id.run_button)
       // setupEditor()
        setupMinimalEditor()
        
        runButton.setOnClickListener {
            runPythonCode()
        }
        testButton = findViewById(R.id.test_button)
        testButton.setOnClickListener{
            startActivity(Intent(this, TestActivity::class.java))
        }
    }
    
    
    private fun setupEditor() {
    
    // 1. Verify files exist
    if (!fileExistsInAssets("textmate/python/syntaxes/python.tmLanguage.json") || 
        !fileExistsInAssets("textmate/python/language-configuration.json")) {
        Toast.makeText(this, "Missing grammar files", Toast.LENGTH_LONG).show()
        return
    }

    // 2. Initialize registries
    FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))
    themeRegistry = ThemeRegistry.getInstance()
    grammarRegistry = GrammarRegistry.getInstance()

    // 3. Load theme
    themeRegistry.setTheme("dark")
   // themeRegistry.setTheme("quietlight") // Light theme
    editor.colorScheme = TextMateColorScheme.create(themeRegistry)
    // 4. Load Python grammar using proper GrammarDefinition
    try {
        val grammarDefinition = object : GrammarDefinition {
            override fun getName() = "python"
            override fun getScopeName() = "source.python"
            override fun getGrammar() = object : IGrammarSource {
                override fun getReader() = InputStreamReader(
                    assets.open("textmate/python/syntaxes/python.tmLanguage.json")
                )
                override fun getFilePath() = "python.tmLanguage.json"
            }
            override fun getLanguageConfiguration() = 
                assets.open("textmate/python/language-configuration.json").reader().use { it.readText() }
        }

        grammarRegistry.loadGrammar(grammarDefinition)
    } catch (e: Exception) {
        Toast.makeText(this, "Error loading grammar: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
        return
    }

    // 5. Basic editor configuration
    editor.apply {
        setEdgeEffectColor(0xFF2196F3.toInt())
        setTextSize(14f)
        setTabWidth(4)
      //  colorScheme = TextMateColorScheme.create(themeRegistry)
    }

    // 6. Create and set language
    val language = TextMateLanguage.create(
        "source.python",
        grammarRegistry,
        themeRegistry,
        true
    )
    editor.setEditorLanguage(language)

    // 7. Set sample code and force refresh
    editor.setText("""
        def main():
            # Hello World
            print("Hello, World!")
            a = 5
            b = 7
            print(f"Sum: {a + b}")
        
        if __name__ == "__main__":
            main()
    """.trimIndent())
    editor.postInvalidate()
}

private fun fileExistsInAssets(path: String): Boolean {
    return try {
        assets.open(path).close()
        true
    } catch (e: Exception) {
        false
    }
}

private fun setupMinimalEditor() {
    FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))
    
    editor.apply {
        setEditorLanguage(TextMateLanguage.create(
            "source.python",
            GrammarRegistry.getInstance().apply {
                loadGrammar(object : GrammarDefinition {
                    override fun getName() = "python"
                    override fun getScopeName() = "source.python"
                    override fun getGrammar() = object : IGrammarSource {
                        override fun getReader() = InputStreamReader(
                            assets.open("textmate/python/syntaxes/python.tmLanguage.json")
                        )
                        override fun getFilePath() = "python.json"
                    }
                    override fun getLanguageConfiguration() = null
                })
            },
            ThemeRegistry.getInstance().apply { setTheme("dark") },
            true
        ))
        setText("def test():\n    print('Hello')")
    }
}

    private fun runPythonCode() {
        
        if (!this::api.isInitialized) {
            Toast.makeText(this, "Please configure API credentials first", Toast.LENGTH_SHORT).show()
            showSettingsDialog()
            return
        }
        
        val code = editor.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, "Please enter some Python code", Toast.LENGTH_SHORT).show()
            return
        }
        
        scope.launch {
            try {
                val output = api.executePythonCode(code)
                showOutputDialog(output)
            } catch (e: Exception) {
                showOutputDialog("Error: ${e.message}")
            }
        }
    }

    private fun showOutputDialog(output: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Execution Result")
                .setMessage(output)
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun checkCredentials() {
        val apiKey = securePrefs.getApiKey()
        val username = securePrefs.getUsername()
        
        if (apiKey == null || username == null) {
            showSettingsDialog()
        } else {
            api = PythonAnywhereApi(apiKey, username)
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("API Configuration Required")
            .setMessage("You need to configure your PythonAnywhere credentials first")
            .setPositiveButton("Configure") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        editor.release()
    }
}