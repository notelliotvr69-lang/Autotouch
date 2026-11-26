package com.vrflex.quest

import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Controller

/**
 * Toggles the VRFlex panel on a single press of Y on the left controller.
 * This only handles input while VRFlex is the active app.
 */
class VRFlexInputSystem(private val onTogglePanel: () -> Unit) : SystemBase() {
    override fun execute() {
        val avatar = Query.where { has(AvatarBody.id) }
            .eval()
            .firstOrNull { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            ?: return

        val leftController = avatar.getComponent<AvatarBody>().leftHand
            .tryGetComponent<Controller>()
            ?: return

        if ((leftController.buttonState and leftController.changedButtons and ButtonBits.ButtonY) != 0) {
            onTogglePanel()
        }
    }
}
