package com.berdik.letmedowngrade

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.berdik.letmedowngrade.utils.PrefManager
import com.berdik.letmedowngrade.utils.XposedChecker
import com.mukesh.MarkDown
import java.io.File

class InstructionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instructions)
        val instructionsMdRenderer = findViewById<ComposeView>(R.id.instructions_md_renderer)

        ViewCompat.setOnApplyWindowInsetsListener(instructionsMdRenderer) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        // Create a temporary file copy of the embedded instructions markdown file.
        val markdownSource: File = File.createTempFile(R.string.app_name.toString(), "tmp")
        PrefManager.loadPrefs()
        if (XposedChecker.isEnabled())
            markdownSource.writeBytes(resources.openRawResource(R.raw.module_loaded).readBytes())
        else
            markdownSource.writeBytes(resources.openRawResource(R.raw.setup_instructions).readBytes())

        // Load the temporary file copy of the instructions in the activity.
        instructionsMdRenderer.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    MarkDown(
                        file = markdownSource,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}