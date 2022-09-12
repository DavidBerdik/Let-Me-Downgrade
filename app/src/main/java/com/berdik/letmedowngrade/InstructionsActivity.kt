package com.berdik.letmedowngrade

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.mukesh.MarkDown
import java.io.File

class InstructionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instructions)

        // Create a temporary file copy of the embedded instructions markdown file.
        val markdownSource: File = File.createTempFile(R.string.app_name.toString(), "tmp")
        markdownSource.writeBytes(resources.openRawResource(R.raw.setup_instructions).readBytes())

        // Load the temporary file copy of the instructions in the activity.
        findViewById<ComposeView>(R.id.instructions_md_renderer).apply {
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