using System;
using BepInEx;
using HarmonyLib;
using UnityEngine;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "ULTRAKILL Arsenal Expansion", "0.7.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private Harmony harmony;

        private void Awake()
        {
            harmony = new Harmony("notelliotvr69.arsenalexpansion030");
            try
            {
                harmony.PatchAll(typeof(Plugin).Assembly);
                Logger.LogInfo("Arsenal Expansion loaded. Reclaimer prototype mechanics are enabled on the current arm: parry to store one projectile, next punch to release it.");
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

        // Existing gear-check shortcut retained separately from the Reclaimer mechanics.
        // This does not guarantee that all weapon variants are permanently unlocked.
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
    /// Prototype of the Reclaimer's capture/release loop, temporarily hooked to the
    /// existing arm's successful parry and punch events until custom arm registration
    /// is integrated. Stores one parried projectile GameObject and releases it on the
    /// next PunchStart.
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
                if (storedProjectile == null)
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
