package com.yourssu.ragent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yourssu.ragent.ui.RAGentApp
import com.yourssu.ragent.ui.theme.RAGentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RAGentTheme {
                RAGentApp()
            }
        }
    }
}
