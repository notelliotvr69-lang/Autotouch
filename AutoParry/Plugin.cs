using System;
using System.Collections.Generic;
using BepInEx;
using BepInEx.Configuration;
using HarmonyLib;
using UnityEngine;

namespace AutoParry
{
    [BepInPlugin("notelliotvr.autoparry", "Auto Parry", "0.1.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private Harmony harmony;
        internal static Plugin Instance;
        internal static ConfigEntry<bool> Enabled;
        internal static ConfigEntry<float> Range;
        internal static ConfigEntry<float> ScanInterval;
        private float nextScan;
        private Type punchType;
        private Type projectileType;
        private System.Reflection.MethodInfo parryProjectile;
        private readonly HashSet<int> attemptedProjectiles = new HashSet<int>();

        private void Awake()
        {
            Instance = this;
            Enabled = Config.Bind("Auto Parry", "Enabled", true, "Automatically attempt to parry nearby projectiles.");
            Range = Config.Bind("Auto Parry", "Range", 3.0f, "Maximum distance from the player to scan for projectiles.");
            ScanInterval = Config.Bind("Auto Parry", "Scan Interval", 0.08f, "Seconds between scans.");
            harmony = new Harmony("notelliotvr.autoparry");
            punchType = AccessTools.TypeByName("Punch");
            projectileType = AccessTools.TypeByName("Projectile");
            parryProjectile = punchType == null ? null : AccessTools.Method(punchType, "ParryProjectile");
            if (punchType == null || projectileType == null || parryProjectile == null)
                Logger.LogWarning("Auto Parry could not resolve Punch.ParryProjectile or Projectile. It will remain inactive; check the game version.");
            else
                Logger.LogInfo("Auto Parry loaded. Prototype scans for nearby projectiles and attempts the game's parry method.");
        }

        private void Update()
        {
            if (!Enabled.Value || Time.unscaledTime < nextScan)
                return;
            nextScan = Time.unscaledTime + Mathf.Clamp(ScanInterval.Value, 0.02f, 0.5f);
            if (punchType == null || projectileType == null || parryProjectile == null)
                return;
            try
            {
                Component player = FindPlayer();
                if (player == null)
                    return;
                UnityEngine.Object[] fists = UnityEngine.Object.FindObjectsOfType(punchType);
                UnityEngine.Object[] projectiles = UnityEngine.Object.FindObjectsOfType(projectileType);
                foreach (UnityEngine.Object fistObject in fists)
                {
                    Component fist = fistObject as Component;
                    if (fist == null || !fist.gameObject.activeInHierarchy)
                        continue;
                    foreach (UnityEngine.Object projectileObject in projectiles)
                    {
                        Component projectile = projectileObject as Component;
                        if (projectile == null || !projectile.gameObject.activeInHierarchy)
                            continue;
                        int id = projectile.GetInstanceID();
                        if (attemptedProjectiles.Contains(id))
                            continue;
                        if (Vector3.Distance(player.transform.position, projectile.transform.position) > Mathf.Clamp(Range.Value, 0.5f, 8f))
                            continue;
                        attemptedProjectiles.Add(id);
                        parryProjectile.Invoke(fist, new object[] { projectile });
                    }
                }
                if (attemptedProjectiles.Count > 2048)
                    attemptedProjectiles.Clear();
            }
            catch (Exception ex)
            {
                Logger.LogDebug("Auto Parry scan skipped: " + ex.GetBaseException().Message);
            }
        }

        private static Component FindPlayer()
        {
            Type movementType = AccessTools.TypeByName("NewMovement");
            if (movementType == null)
                return null;
            return UnityEngine.Object.FindObjectOfType(movementType) as Component;
        }

        private void OnDestroy()
        {
            if (harmony != null)
            {
                harmony.UnpatchSelf();
                harmony = null;
            }
            Instance = null;
        }
    }
}