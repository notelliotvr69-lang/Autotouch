package com.vrflex.quest

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Box
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.Plane
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.vr.VRFeature

class ImmersiveActivity : AppSystemActivity() {
    private var panelVisible = true
    private val launcherTab = mutableStateOf("GAMES")

    private fun toggleModMenu() {
        launcherTab.value = "MOD MENU"
        panelVisible = !panelVisible
        Entity(R.id.vrflex_main_panel).setComponent(Visible(panelVisible))
    }

    private fun openGameSwitcher() {
        launcherTab.value = "GAMES"
        panelVisible = true
        Entity(R.id.vrflex_main_panel).setComponent(Visible(true))
    }

    override fun registerFeatures(): List<SpatialFeature> =
        listOf(VRFeature(this), ComposeFeature())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemManager.registerSystem(VRFlexInputSystem(::toggleModMenu, ::openGameSwitcher))
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setLightingEnvironment(
            ambientColor = Vector3(0.12f, 0.12f, 0.12f),
            sunColor = Vector3(2.2f, 2.2f, 2.2f),
            sunDirection = Vector3(-1.0f, -2.0f, -1.0f),
            environmentIntensity = 0.25f
        )
        scene.setViewOrigin(0f, 0f, 0f, 0f)

        Entity.create(listOf(
            Mesh(Uri.parse("mesh://plane")), Plane(width = 12f, depth = 12f),
            Material().apply { baseColor = Color4(0.035f, 0.035f, 0.04f, 1f) },
            Transform(Pose(Vector3(0f, -1.35f, 0f)))
        ))
        Entity.create(listOf(
            Mesh(Uri.parse("mesh://box")),
            Box(Vector3(-3.2f, -0.08f, -0.12f), Vector3(3.2f, 0.08f, 0.12f)),
            Material().apply { baseColor = Color4(0.95f, 0.34f, 0.035f, 1f) },
            Transform(Pose(Vector3(0f, -0.72f, -2.8f)))
        ))
        Entity.create(listOf(
            Mesh(Uri.parse("mesh://box")),
            Box(Vector3(-0.035f, -0.75f, -0.06f), Vector3(0.035f, 0.75f, 0.06f)),
            Material().apply { baseColor = Color4(0.95f, 0.34f, 0.035f, 1f) },
            Transform(Pose(Vector3(-2.6f, 0f, -2.8f)))
        ))
        Entity.create(listOf(
            Mesh(Uri.parse("mesh://box")),
            Box(Vector3(-0.035f, -0.75f, -0.06f), Vector3(0.035f, 0.75f, 0.06f)),
            Material().apply { baseColor = Color4(0.95f, 0.34f, 0.035f, 1f) },
            Transform(Pose(Vector3(2.6f, 0f, -2.8f)))
        ))

        // Start directly ahead; VRFlexInputSystem attaches the head-follow
        // behavior as soon as Meta Spatial publishes the local avatar.
        Entity.create(listOf(
            Panel(panelRegistrationId = R.id.vrflex_main_panel),
            Transform(Pose(Vector3(0f, 0.0f, -1.65f))),
            Visible(panelVisible)
        ))
    }

    override fun registerPanels() = listOf(
        ComposeViewPanelRegistration(
            R.id.vrflex_main_panel,
            composeViewCreator = { _, context ->
                ComposeView(context).apply { setContent { VRFlexLauncher(launcherTab) } }
            },
            settingsCreator = {
                UIPanelSettings(shape = QuadShapeOptions(width = 1.35f, height = 1.0f))
            }
        )
    )
}
