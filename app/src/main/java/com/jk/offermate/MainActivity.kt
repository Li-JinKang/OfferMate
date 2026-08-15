package com.jk.offermate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jk.offermate.ui.navigation.OfferMateApp
import com.jk.offermate.ui.theme.OfferMateTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OfferMateApplication).container
        setContent {
            OfferMateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OfferMateApp(container)
                }
            }
        }
    }
}
