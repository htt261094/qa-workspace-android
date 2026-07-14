package vn.baokim.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import vn.baokim.qa.ui.QaApp
import vn.baokim.qa.ui.theme.QaWorkspaceTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QaWorkspaceTheme {
                QaApp()
            }
        }
    }
}
