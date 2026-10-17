using System;
using BepInEx;
using HarmonyLib;
using UnityEngine;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.reclaimer", "ULTRAKILL Reclaimer", "0.7.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private Harmony harmony;

        private void Awake()
        {
            harmony = new Harmony("notelliotvr69.arsenalexpansion030");
            try
            {
                harmony.PatchAll(typeof(Plugin).Assembly);
                UnlockSpecialWeaponVariants();
                Logger.LogInfo("Reclaimer loaded. Special weapon variants enabled: Slab Revolver, Jackhammer, and Sawblade Launcher. Reclaimer arm slot prototype enabled: red placeholder; parry with Feedbacker, release with Knuckleblaster.");
            }
            catch (Exception ex)
            {
                Logger.LogError("Failed to apply patches: " + ex);
            }
        }

        private void OnDestroy()
        {
            ReclaimerMechanics.ClearStoredProjectile();
            if (harmony != null)
            {
                harmony.UnpatchSelf();
                harmony = null;
            }
        }

        // Persist the three alternate weapon unlocks and select their alternate forms.
        // Gear names come from GameProgressMoneyAndGear; weapon.* preferences control selection.
        private void UnlockSpecialWeaponVariants()
        {
            try
            {
                GameProgressSaver.AddGear("revalt");
                GameProgressSaver.AddGear("shoalt");
                GameProgressSaver.AddGear("naialt");

                MonoSingleton<PrefsManager>.Instance.SetInt("weapon.rev0", 2);
                MonoSingleton<PrefsManager>.Instance.SetInt("weapon.sho0", 2);
                MonoSingleton<PrefsManager>.Instance.SetInt("weapon.nai0", 2);

                Logger.LogInfo("Saved special variant unlocks and selected alternate Revolver, Shotgun, and Nailgun forms.");
            }
            catch (Exception ex)
            {
                Logger.LogWarning("Could not persist special weapon variant unlocks yet: " + ex.Message);
            }
        }

        // Gear-check shortcut also exposes gear to shop/variant checks while the mod is active.
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

    /// <summary>
    /// Adds one selectable red Reclaimer slot using the game's stock arm model as a temporary
    /// placeholder. This is an adaptation of the public custom-arm proof of concept's
    /// ArmChange variation hook, without its UMM dependency.
    /// </summary>
    internal static class ReclaimerArmSlot
    {
        private static bool slotSelected;
        private static int variation = -1;
        private static Color originalIconColor = Color.white;
        private static bool savedIconColor;

        internal static bool IsSelected => slotSelected;

        [HarmonyPatch(typeof(FistControl), nameof(FistControl.ArmChange))]
        private static class ArmChangePatch
        {
            private static void Prefix(ref int orderNum)
            {
                // The custom variation is appended to the Standard/blue arm cycle.
                if (orderNum == 1)
                {
                    if (variation < 0)
                        return;
                    variation = -1;
                    slotSelected = false;
                }
                else if (orderNum == 0 && !slotSelected)
                {
                    variation++;
                    if (variation == 0)
                    {
                        slotSelected = true;
                        orderNum = 0;
                    }
                }
                else if (orderNum == 0 && slotSelected)
                {
                    slotSelected = false;
                    variation = -1;
                }
            }

            private static void Postfix(FistControl __instance)
            {
                if (__instance == null)
                    return;

                if (slotSelected)
                {
                    if (__instance.fistIcon != null)
                    {
                        if (!savedIconColor)
                        {
                            originalIconColor = __instance.fistIcon.color;
                            savedIconColor = true;
                        }
                        __instance.fistIcon.color = new Color32(190, 35, 45, 255);
                    }

                    // No custom mesh is required yet: keep the current stock arm as a placeholder.
                    if (__instance.currentArmObject != null)
                    {
                        foreach (Renderer renderer in __instance.currentArmObject.GetComponentsInChildren<Renderer>(true))
                        {
                            if (renderer != null && renderer.material != null)
                                renderer.material.color = new Color32(225, 183, 62, 255);
                        }
                    }
                }
                else if (__instance.fistIcon != null && savedIconColor)
                {
                    __instance.fistIcon.color = originalIconColor;
                }
            }
        }
    }

    /// <summary>
    /// Prototype of the Reclaimer capture/release loop: Feedbacker stores one successfully
    /// parried projectile; Knuckleblaster releases it on its next punch. Custom arm
    /// registration is not integrated yet.
    /// </summary>
    internal static class ReclaimerMechanics
    {
        private static GameObject storedProjectile;

        internal static void ClearStoredProjectile()
        {
            if (storedProjectile != null)
                UnityEngine.Object.Destroy(storedProjectile);
            storedProjectile = null;
        }

        [HarmonyPatch(typeof(Punch), "ParryProjectile")]
        private static class CaptureParriedProjectile
        {
            private static void Postfix(Projectile proj)
            {
                if (proj == null || storedProjectile != null)
                    return;

                // ParryProjectile has already succeeded at this point. Take ownership
                // of the now-parried projectile rather than allowing it to fly away.
                storedProjectile = proj.gameObject;
                storedProjectile.transform.SetParent(null);
                foreach (Projectile projectile in storedProjectile.GetComponentsInChildren<Projectile>(true))
                {
                    projectile.playerBullet = true;
                    projectile.friendly = true;
                    projectile.undeflectable = false;
                    projectile.homingType = HomingType.None;
                    projectile.target = null;
                    projectile.hittingPlayer = false;
                }
                storedProjectile.SetActive(false);
            }
        }

        [HarmonyPatch(typeof(Punch), "PunchStart")]
        private static class ReleaseStoredProjectile
        {
            private static void Postfix(Punch __instance)
            {
                // FistType.Heavy is the Knuckleblaster. Feedbacker punches must not
                // consume the stored projectile; only the Knuckleblaster releases it.
                if (__instance == null || __instance.type != FistType.Heavy || storedProjectile == null)
                    return;

                GameObject projectileObject = storedProjectile;
                storedProjectile = null;

                projectileObject.transform.position = __instance.transform.position + (2f * __instance.transform.forward);
                projectileObject.transform.rotation = CameraController.Instance != null
                    ? CameraController.Instance.transform.rotation
                    : __instance.transform.rotation;

                if (CameraFrustumTargeter.Instance != null && CameraFrustumTargeter.Instance.CurrentTarget != null)
                    projectileObject.transform.LookAt(CameraFrustumTargeter.Instance.CurrentTarget.bounds.center);

                foreach (Projectile projectile in projectileObject.GetComponentsInChildren<Projectile>(true))
                {
                    projectile.playerBullet = true;
                    projectile.friendly = true;
                    projectile.undeflectable = false;
                    projectile.homingType = HomingType.None;
                    projectile.target = null;
                    projectile.hittingPlayer = false;
                }

                projectileObject.SetActive(true);
            }
        }
    }
}
