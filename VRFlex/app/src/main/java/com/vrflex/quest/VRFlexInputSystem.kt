package com.vrflex.quest

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Followable
import com.meta.spatial.toolkit.FollowableType

/**
 * Left Y toggles the VRFlex mod menu.
 * Left Menu opens the game/app switcher.
 *
 * Avatar discovery can happen after onSceneReady, so the panel anchor is
 * retried here until the local tracked head is available.
 */
class VRFlexInputSystem(
    private val onToggleModMenu: () -> Unit,
    private val onOpenGameSwitcher: () -> Unit
) : SystemBase() {
    private var panelAnchored = false

    override fun execute() {
        val avatar = Query.where { has(AvatarBody.id) }
            .eval()
            .firstOrNull { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            ?: return

        val body = avatar.getComponent<AvatarBody>()
        if (!panelAnchored) {
            val head = body.head
            if (head != Entity.nullEntity()) {
                Entity(R.id.vrflex_main_panel).setComponent(
                    Followable(
                        target = head,
                        offset = Pose(Vector3(0f, 0f, -1.65f)),
                        type = FollowableType.FACE,
                        tolerance = 0.12f,
                        speed = 2.0f
                    )
                )
                panelAnchored = true
            }
        }

        val leftController = body.leftHand.tryGetComponent<Controller>() ?: return
        if ((leftController.buttonState and leftController.changedButtons and ButtonBits.ButtonY) != 0) {
            onToggleModMenu()
        }
        if ((leftController.buttonState and leftController.changedButtons and ButtonBits.ButtonMenu) != 0) {
            onOpenGameSwitcher()
        }
    }
}
