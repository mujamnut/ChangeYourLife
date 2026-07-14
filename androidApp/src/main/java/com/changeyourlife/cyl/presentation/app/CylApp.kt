package com.changeyourlife.cyl.presentation.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.changeyourlife.cyl.data.local.session.AppThemeMode
import com.changeyourlife.cyl.presentation.navigation.CylNavHost
import com.changeyourlife.cyl.presentation.theme.ChangeYourLifeTheme

@Composable
fun CylApp(themeMode: AppThemeMode = AppThemeMode.SYSTEM) {
    ChangeYourLifeTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CylNavHost()
        }
    }
}

