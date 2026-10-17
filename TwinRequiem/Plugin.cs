using System;
using System.Reflection;
using BepInEx;
using BepInEx.Configuration;
using HarmonyLib;
using UnityEngine;

namespace TwinRequiem
{
    [BepInPlugin("notelliotvr69.twinrequiem", "TWIN REQUIEM", "0.1.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static Plugin Instance;
        internal static ConfigEntry<string> Variant;
        internal static ConfigEntry<float> RhythmWindow;
        internal static ConfigEntry<bool> EnablePrototypeHook;
        private Harmony harmony;

        private void Awake()
        {
            Instance = this;
            Variant = Config.Bind("Twin Requiem", "Variant", "Requiem", "Prototype variant: Requiem, Dirge, Crescendo, or Encore.");
            RhythmWindow = Config.Bind("Twin Requiem", "Rhythm Window", 0.42f, "Seconds between alternating shots for a rhythm bonus.");
            EnablePrototypeHook = Config.Bind("Prototype", "Hook Existing Revolver", true, "Temporarily test alternating-shot timing on the stock revolver while the custom weapon slot is developed.");
            harmony = new Harmony("notelliotvr69.twinrequiem");
            try
            {
                if (EnablePrototypeHook.Value)
                    harmony.PatchAll(typeof(Plugin).Assembly);
                Logger.LogInfo("TWIN REQUIEM v0.1.0 loaded. Variant: " + Variant.Value + ". Prototype hook: " + EnablePrototypeHook.Value + ". This build is a mechanics foundation; custom slot, dual Slab Revolver models, and projectile behavior are not implemented yet.");
            }
            catch (Exception ex) { Logger.LogError("TWIN REQUIEM failed to initialize: " + ex); }
        }

        private void OnDestroy()
        {
            if (harmony != null) { harmony.UnpatchSelf(); harmony = null; }
            RequiemRhythm.Reset();
            Instance = null;
        }
    }

    internal static class RequiemRhythm
    {
        private static float lastShotTime = -1000f;
        private static int nextHand;
        private static int streak;

        internal static void RegisterShot()
        {
            float now = Time.time;
            float window = Plugin.RhythmWindow != null ? Mathf.Max(0.08f, Plugin.RhythmWindow.Value) : 0.42f;
            bool onBeat = now - lastShotTime <= window;
            string variant = Plugin.Variant != null ? Plugin.Variant.Value.Trim() : "Requiem";
            if (string.Equals(variant, "Dirge", StringComparison.OrdinalIgnoreCase)) onBeat = now - lastShotTime <= window * 1.6f;
            else if (string.Equals(variant, "Crescendo", StringComparison.OrdinalIgnoreCase)) onBeat = now - lastShotTime <= window * 0.72f;
            if (lastShotTime > -999f && onBeat) streak++; else streak = 0;
            nextHand = 1 - nextHand;
            lastShotTime = now;
            if (Plugin.Instance != null && streak > 0 && streak % 4 == 0)
                Plugin.Instance.Logger.LogInfo("TWIN REQUIEM cadence: " + variant + " | alternating hand " + (nextHand == 0 ? "LEFT" : "RIGHT") + " | rhythm streak " + streak + ".");
        }

        internal static void Reset() { lastShotTime = -1000f; nextHand = 0; streak = 0; }
    }

    // Reflection-based target lookup avoids binding to an uncertain overload at compile time.
    // This prototype observes the stock Revolver Shoot entry point; it does not replace the gun.
    [HarmonyPatch]
    internal static class StockRevolverPrototypeHook
    {
        private static MethodBase TargetMethod()
        {
            Type revolver = AccessTools.TypeByName("Revolver");
            return revolver == null ? null : AccessTools.Method(revolver, "Shoot");
        }
        private static void Prefix() { RequiemRhythm.RegisterShot(); }
    }
}
