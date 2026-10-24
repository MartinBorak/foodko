package app.foodko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.foodko.ui.FoodkoRoot
import app.foodko.ui.theme.FoodkoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodkoTheme {
                FoodkoRoot(appContainer)
            }
        }
    }
}
