package com.david.catalanpdfreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.david.catalanpdfreader.ui.navigation.AppNavigation
import com.david.catalanpdfreader.ui.theme.CatalanPDFReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatalanPDFReaderTheme {
                AppNavigation()
            }
        }
    }
}
