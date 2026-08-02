package com.greenie.auto.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.greenie.auto.shared.GreenieApp
import com.greenie.auto.shared.initAndroidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAndroidContext(this)
        setContent { GreenieApp() }
    }
}
