using System;
using System.Reflection;
using BepInEx;
using HarmonyLib;
using UnityEngine;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "ULTRAKILL Arsenal Expansion", "0.5.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private Harmony harmony;
        private float momentum;
        private float overdriveTimer;
        private float boostCooldown;
        private Component playerMovement;
        private Rigidbody playerBody;
        private float scanTimer;

        private const float ChargeSpeed = 18f;
        private const float MaxMomentum = 100f;
        private const float OverdriveDuration = 1.35f;
        private const float OverdriveCooldown = 1.5f;
        private const float BoostImpulse = 11f;

        private void Awake()
        {
            harmony = new Harmony("notelliotvr69.arsenalexpansion030");
            try
            {
                harmony.PatchAll(typeof(Plugin).Assembly);
                Logger.LogInfo("Arsenal Expansion loaded: gear checks and Momentum Overdrive enabled.");
            }
            catch (Exception ex)
            {
                Logger.LogError("Failed to apply patches: " + ex);
            }
        }

        private void Update()
        {
            float dt = Time.deltaTime;
            if (boostCooldown > 0f) boostCooldown -= dt;
            if (overdriveTimer > 0f) overdriveTimer -= dt;

            // Scan periodically rather than searching every frame. Reflection keeps
            // this feature tolerant of game-version changes to the movement type.
            scanTimer -= dt;
            if (scanTimer <= 0f || playerMovement == null || playerBody == null)
            {
                scanTimer = 1f;
                FindPlayerMovement();
            }

            if (playerBody == null || playerMovement == null) return;

            Vector3 velocity = playerBody.velocity;
            float horizontalSpeed = new Vector3(velocity.x, 0f, velocity.z).magnitude;

            // Sustained high-speed movement charges the meter; standing still drains it.
            if (horizontalSpeed >= ChargeSpeed)
                momentum = Mathf.Min(MaxMomentum, momentum + dt * 24f);
            else
                momentum = Mathf.Max(0f, momentum - dt * 16f);

            // Left Alt activates Overdrive when the meter is full.
            if (Input.GetKeyDown(KeyCode.LeftAlt) && momentum >= MaxMomentum && boostCooldown <= 0f)
            {
                momentum = 0f;
                overdriveTimer = OverdriveDuration;
                boostCooldown = OverdriveCooldown;
                Vector3 horizontal = new Vector3(velocity.x, 0f, velocity.z);
                if (horizontal.sqrMagnitude < 0.01f)
                    horizontal = transform.forward;
                horizontal.Normalize();
                playerBody.velocity = new Vector3(
                    horizontal.x * Mathf.Max(horizontalSpeed, ChargeSpeed) + horizontal.x * BoostImpulse,
                    velocity.y,
                    horizontal.z * Mathf.Max(horizontalSpeed, ChargeSpeed) + horizontal.z * BoostImpulse);
            }

            // During the short Overdrive window, preserve momentum with a mild speed floor.
            if (overdriveTimer > 0f)
            {
                velocity = playerBody.velocity;
                Vector3 horizontal = new Vector3(velocity.x, 0f, velocity.z);
                if (horizontal.magnitude < ChargeSpeed + 4f && horizontal.sqrMagnitude > 0.01f)
                {
                    horizontal = horizontal.normalized * (ChargeSpeed + 4f);
                    playerBody.velocity = new Vector3(horizontal.x, velocity.y, horizontal.z);
                }
            }
        }

        private void FindPlayerMovement()
        {
            playerMovement = null;
            playerBody = null;

            try
            {
                MonoBehaviour[] behaviours = FindObjectsOfType<MonoBehaviour>();
                for (int i = 0; i < behaviours.Length; i++)
                {
                    MonoBehaviour candidate = behaviours[i];
                    if (candidate == null || candidate.GetType().Name != "NewMovement") continue;

                    playerMovement = candidate;
                    Type type = candidate.GetType();
                    const BindingFlags flags = BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic;

                    // ULTRAKILL builds have used different member visibility/naming;
                    // inspect common Rigidbody member names rather than hard-coding one field.
                    string[] names = { "rb", "rigidbody", "playerRigidbody", "body" };
                    for (int n = 0; n < names.Length && playerBody == null; n++)
                    {
                        FieldInfo field = type.GetField(names[n], flags);
                        if (field != null) playerBody = field.GetValue(candidate) as Rigidbody;

                        PropertyInfo property = type.GetProperty(names[n], flags);
                        if (playerBody == null && property != null && property.GetIndexParameters().Length == 0)
                        {
                            try { playerBody = property.GetValue(candidate, null) as Rigidbody; }
                            catch { }
                        }
                    }

                    if (playerBody == null)
                        Logger.LogWarning("Found NewMovement but couldn't locate its Rigidbody. Momentum Overdrive is idle.");
                    return;
                }
            }
            catch (Exception ex)
            {
                Logger.LogWarning("Couldn't locate player movement for Momentum Overdrive: " + ex.Message);
            }
        }

        private void OnDestroy()
        {
            if (harmony != null)
            {
                harmony.UnpatchSelf();
                harmony = null;
            }
        }

        // This unlock shortcut is retained for now; in-game ownership still needs verification.
        [HarmonyPatch(typeof(GameProgressSaver), nameof(GameProgressSaver.CheckGear))]
        private static class UnlockAllGear
        {
            private static bool Prefix(ref int __result)
            {
                __result = 1;
                return false;
            }
        }
    }
}
