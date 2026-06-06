package com.toybox.minipos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.toybox.minipos.navigation.NavGraph
import com.toybox.minipos.ui.theme.MiniPosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiniPosTheme {
                NavGraph()
            }
        }
    }
}
