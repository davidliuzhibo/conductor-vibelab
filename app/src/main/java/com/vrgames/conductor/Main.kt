package com.vrgames.conductor

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.conductor.content.HallStage
import com.vrgames.conductor.content.LEVELS_ID
import com.vrgames.conductor.content.PiecesWindow

/** Two linked spaces: the podium in the hall (a full-immersion Stage) and the window with pieces and trophies. */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { HallStage() }
        }
        WindowContainer(LEVELS_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { PiecesWindow() }
        }
    }
