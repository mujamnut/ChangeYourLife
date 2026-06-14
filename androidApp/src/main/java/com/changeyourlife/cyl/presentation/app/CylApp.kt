package com.changeyourlife.cyl.presentation.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.changeyourlife.cyl.presentation.navigation.CylNavHost
import com.changeyourlife.cyl.presentation.theme.ChangeYourLifeTheme

@Composable
fun CylApp() {
    ChangeYourLifeTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CylNavHost()
        }
    }
}

