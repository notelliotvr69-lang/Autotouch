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
import com.meta.spatial.toolkit.Transform
import kotlin.math.abs

/**
 * Live arm reach adjustment for tracked hands inside VRFlex only.
 * It cannot alter tracking used by other independently running Quest games.
 */
object ArmLengthConfig {
    @Volatile var factor: Float = 1.0f
}

class VRFlexInputSystem(
    private val onToggleModMenu: () -> Unit,
    private val onOpenGameSwitcher: () -> Unit
) : SystemBase() {
    private data class HandPoseState(
        var base: Vector3? = null,
        var lastApplied: Vector3? = null
    )

    private var panelAnchored = false
    private val leftPose = HandPoseState()
    private val rightPose = HandPoseState()

    private fun adjustHand(hand: Entity, headPosition: Vector3, state: HandPoseState) {
        val transform = hand.tryGetComponent<Transform>() ?: return
        val current = transform.transform.t
        val last = state.lastApplied
        val base = state.base
        val isOurPreviousOutput = last != null &&
            abs(current.x - last.x) < 0.0001f &&
            abs(current.y - last.y) < 0.0001f &&
            abs(current.z - last.z) < 0.0001f
        val raw = if (isOurPreviousOutput && base != null) base else current
        state.base = raw

        val scale = ArmLengthConfig.factor.coerceIn(0.5f, 1.5f)
        val adjusted = Vector3(
            headPosition.x + (raw.x - headPosition.x) * scale,
            headPosition.y + (raw.y - headPosition.y) * scale,
            headPosition.z + (raw.z - headPosition.z) * scale
        )
        transform.transform.t = adjusted
        hand.setComponent(transform)
        state.lastApplied = Vector3(adjusted.x, adjusted.y, adjusted.z)
    }

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

        val headTransform = body.head.tryGetComponent<Transform>()
        if (headTransform != null) {
            val headPosition = headTransform.transform.t
            adjustHand(body.leftHand, headPosition, leftPose)
            adjustHand(body.rightHand, headPosition, rightPose)
        }

        val head = body.head.tryGetComponent<Transform>()?.transform?.t ?: Vector3(0f, 0f, 0f)
        val left = body.leftHand.tryGetComponent<Transform>()?.transform?.t ?: head
        val right = body.rightHand.tryGetComponent<Transform>()?.transform?.t ?: head
        val leftController = body.leftHand.tryGetComponent<Controller>()
        val rightController = body.rightHand.tryGetComponent<Controller>()
        val buttons = (leftController?.buttonState ?: 0) or (rightController?.buttonState ?: 0)

        // Send current tracked positions and button state to the phone host.
        // This is telemetry only; Android does not inject it into arbitrary apps.
        PhoneHostStreamClient.sendTracking(
            floatArrayOf(head.x, head.y, head.z),
            floatArrayOf(left.x, left.y, left.z),
            floatArrayOf(right.x, right.y, right.z),
            buttons
        )

        if (leftController != null &&
            (leftController.buttonState and leftController.changedButtons and ButtonBits.ButtonY) != 0) {
            onToggleModMenu()
        }
        if (leftController != null &&
            (leftController.buttonState and leftController.changedButtons and ButtonBits.ButtonMenu) != 0) {
            onOpenGameSwitcher()
        }
    }
}
