package com.vrflex.quest

import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Controller

/**
 * Left Y toggles the VRFlex mod menu.
 * Left Menu opens the game/app switcher.
 * These shortcuts are active only while VRFlex is the foreground app.
 */
class VRFlexInputSystem(
    private val onToggleModMenu: () -> Unit,
    private val onOpenGameSwitcher: () -> Unit
) : SystemBase() {
    override fun execute() {
        val avatar = Query.where { has(AvatarBody.id) }
            .eval()
            .firstOrNull { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            ?: return

        val leftController = avatar.getComponent<AvatarBody>().leftHand
            .tryGetComponent<Controller>()
            ?: return

        if ((leftController.buttonState and leftController.changedButtons and ButtonBits.ButtonY) != 0) {
            onToggleModMenu()
        }
        if ((leftController.buttonState and leftController.changedButtons and ButtonBits.ButtonMenu) != 0) {
            onOpenGameSwitcher()
        }
    }
}
