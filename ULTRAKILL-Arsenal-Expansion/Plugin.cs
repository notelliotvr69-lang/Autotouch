using System;
using BepInEx;
using HarmonyLib;
using UnityEngine;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "ULTRAKILL Arsenal Expansion", "0.6.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private Harmony harmony;

        private void Awake()
        {
            harmony = new Harmony("notelliotvr69.arsenalexpansion030");
            try
            {
                harmony.PatchAll(typeof(Plugin).Assembly);
                Logger.LogInfo("Arsenal Expansion loaded. Reclaimer arm work is in progress; no momentum mechanic is enabled.");
            }
            catch (Exception ex)
            {
                Logger.LogError("Failed to apply patches: " + ex);
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

        // Existing gear-check shortcut retained separately from the Reclaimer arm.
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
}
