using System;
using BepInEx;
using BepInEx.Configuration;
using UnityEngine;

namespace WhiplashTetherThrow
{
    [BepInPlugin("notelliotvr69.whiplashtetherthrow", "Whiplash Tether Throw", "0.1.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private ConfigEntry<KeyCode> holdKey;
        private ConfigEntry<float> grabRange;
        private ConfigEntry<float> aimTurnSpeed;
        private ConfigEntry<float> throwImpulse;
        private ConfigEntry<float> heavyMassThreshold;
        private Camera playerCamera;
        private Rigidbody target;
        private Vector3 aimDirection;
        private float heldSeconds;
        private bool tethering;

        private void Awake()
        {
            holdKey = Config.Bind("Controls", "Tether hold key", KeyCode.Mouse1, "Hold to grab and aim; release to throw or pull toward a heavy enemy.");
            grabRange = Config.Bind("Mechanic", "Grab range", 35f, "Maximum grab distance.");
            aimTurnSpeed = Config.Bind("Mechanic", "Aim turn speed", 110f, "Maximum aim adjustment in degrees per second.");
            throwImpulse = Config.Bind("Mechanic", "Throw impulse", 32f, "Impulse applied to lighter targets.");
            heavyMassThreshold = Config.Bind("Mechanic", "Heavy mass threshold", 45f, "Targets this heavy pull the player toward them when released.");
            Logger.LogInfo("WhiplashTetherThrow loaded. Hold " + holdKey.Value + " while the Whiplash is equipped.");
        }

        private void Update()
        {
            if (playerCamera == null) playerCamera = FindPlayerCamera();
            if (playerCamera == null) return;
            if (!IsWhiplashEquipped()) { if (tethering) CancelTether(); return; }
            if (Input.GetKeyDown(holdKey.Value)) BeginTether();
            if (tethering && Input.GetKey(holdKey.Value)) AimTether();
            if (tethering && Input.GetKeyUp(holdKey.Value)) ReleaseTether();
        }

        private void BeginTether()
        {
            RaycastHit hit;
            if (!Physics.Raycast(playerCamera.transform.position, playerCamera.transform.forward, out hit, grabRange.Value, Physics.DefaultRaycastLayers, QueryTriggerInteraction.Ignore)) return;
            Rigidbody body = hit.rigidbody;
            if (body == null || body.isKinematic || IsPlayerBody(body) || !LooksLikeEnemy(body.transform.root.gameObject)) return;
            target = body; tethering = true; heldSeconds = 0f; aimDirection = playerCamera.transform.forward;
            target.velocity = Vector3.zero; target.angularVelocity = Vector3.zero;
        }

        private void AimTether()
        {
            if (target == null) { CancelTether(); return; }
            heldSeconds += Time.deltaTime;
            aimDirection = Vector3.RotateTowards(aimDirection, playerCamera.transform.forward, aimTurnSpeed.Value * Mathf.Deg2Rad * Time.deltaTime, 0f).normalized;
            Vector3 point = playerCamera.transform.position + aimDirection * Mathf.Clamp(Vector3.Distance(playerCamera.transform.position, target.worldCenterOfMass), 2f, 7f);
            target.velocity = Vector3.ClampMagnitude((point - target.worldCenterOfMass) * 10f, 22f);
            target.angularVelocity = Vector3.zero;
        }

        private void ReleaseTether()
        {
            Rigidbody released = target; target = null; tethering = false;
            if (released == null) return;
            if (released.mass >= heavyMassThreshold.Value)
            {
                Component movement = FindPlayerMovement();
                Rigidbody playerBody = movement == null ? null : movement.GetComponent<Rigidbody>();
                if (playerBody != null && !playerBody.isKinematic)
                    playerBody.AddForce((released.worldCenterOfMass - playerCamera.transform.position).normalized * Mathf.Clamp(released.mass * 1.4f, 35f, 100f), ForceMode.VelocityChange);
                else Logger.LogWarning("Heavy target detected, but player Rigidbody was not found; pull behavior skipped.");
            }
            else
            {
                released.velocity = Vector3.zero;
                released.AddForce(aimDirection * throwImpulse.Value * Mathf.Clamp(1f + heldSeconds * 0.12f, 1f, 1.8f), ForceMode.VelocityChange);
                released.gameObject.AddComponent<ThrownImpactMarker>();
            }
        }

        private void CancelTether() { tethering = false; target = null; heldSeconds = 0f; }
        private Camera FindPlayerCamera()
        {
            Component movement = FindPlayerMovement();
            if (movement != null) { Camera child = movement.GetComponentInChildren<Camera>(); if (child != null) return child; }
            Camera[] cameras = FindObjectsOfType<Camera>();
            foreach (Camera c in cameras) if (c != null && c.enabled && c.gameObject.activeInHierarchy) return c;
            return null;
        }
        private Component FindPlayerMovement()
        {
            MonoBehaviour[] all = FindObjectsOfType<MonoBehaviour>();
            foreach (MonoBehaviour c in all) if (c != null && c.GetType().Name == "NewMovement") return c;
            return null;
        }
        private bool IsWhiplashEquipped()
        {
            MonoBehaviour[] all = FindObjectsOfType<MonoBehaviour>();
            foreach (MonoBehaviour c in all)
            {
                if (c == null || !c.gameObject.activeInHierarchy) continue;
                string n = c.GetType().Name;
                if ((n.IndexOf("Whiplash", StringComparison.OrdinalIgnoreCase) >= 0 || n.IndexOf("HookArm", StringComparison.OrdinalIgnoreCase) >= 0) && playerCamera.transform.root == c.transform.root) return true;
            }
            return false;
        }
        private bool IsPlayerBody(Rigidbody body)
        {
            Component movement = FindPlayerMovement();
            return movement != null && (body.transform == movement.transform || body.transform.IsChildOf(movement.transform));
        }
        private bool LooksLikeEnemy(GameObject obj)
        {
            Component[] components = obj.GetComponentsInChildren<Component>(true);
            foreach (Component c in components) if (c != null && (c.GetType().Name.IndexOf("EnemyIdentifier", StringComparison.OrdinalIgnoreCase) >= 0 || c.GetType().Name.IndexOf("Enemy", StringComparison.OrdinalIgnoreCase) >= 0)) return true;
            return false;
        }
    }

    internal sealed class ThrownImpactMarker : MonoBehaviour
    {
        private void OnCollisionEnter(Collision collision)
        {
            if (collision != null && collision.relativeVelocity.magnitude > 5f && collision.gameObject != null)
            {
                // Enemy-only collision damage is deliberately not guessed here; wire it to the verified game damage signature.
                Destroy(this);
            }
        }
    }
}
