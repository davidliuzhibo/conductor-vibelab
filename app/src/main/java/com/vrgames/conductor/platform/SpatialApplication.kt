package com.vrgames.conductor.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.conductor.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
