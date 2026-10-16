using System;
using BepInEx;
using HarmonyLib;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "ULTRAKILL Arsenal Expansion", "0.4.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private Harmony harmony;

        private void Awake()
        {
            harmony = new Harmony("notelliotvr69.arsenalexpansion030");
            try
            {
                harmony.PatchAll(typeof(Plugin).Assembly);
                Logger.LogInfo("Arsenal Expansion loaded: enabling weapon and variant gear checks.");
            }
            catch (Exception ex)
            {
                Logger.LogError("Failed to apply unlock patches: " + ex);
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

        // ULTRAKILL uses this check to determine whether gear and weapon variants
        // are unlocked. Return the game's normal "owned/unlocked" value without
        // writing permanent changes to the player's save file.
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
