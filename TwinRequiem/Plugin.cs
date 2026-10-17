using System;
using System.Reflection;
using BepInEx;
using BepInEx.Configuration;
using HarmonyLib;
using UnityEngine;

namespace TwinRequiem
{
    [BepInPlugin("notelliotvr69.twinrequiem", "TWIN REQUIEM", "0.3.1")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static Plugin Instance;
        internal static ConfigEntry<string> Variant;
        internal static ConfigEntry<bool> ApplyVisualTheme;
        internal static RequiemRuntime Runtime;
        private Harmony harmony;

        private void Awake()
        {
            Logger.LogInfo("TWIN REQUIEM v0.3.1 Awake entered.");
            try
            {
                Instance = this;
                Variant = Config.Bind("Twin Requiem", "Variant", "Requiem",
                    "Visual palette prototype: Requiem, Dirge, Crescendo, or Encore.");
                ApplyVisualTheme = Config.Bind("Prototype", "Tint Stock Revolver",
                    false, "Experimental preview only: tints the stock Revolver model. Disabled by default.");
                Runtime = new RequiemRuntime(RequiemRules.ParseVariant(Variant.Value));
                harmony = new Harmony("notelliotvr69.twinrequiem");
                if (ApplyVisualTheme.Value)
                    harmony.PatchAll(typeof(Plugin).Assembly);

                Logger.LogInfo("TWIN REQUIEM v0.3.1 mechanics runtime initialized. Target slot: " +
                    RequiemRules.TargetWeaponSlot + ", variant: " + Runtime.CurrentVariant +
                    ", shot interval: " + Runtime.Tuning.ShotInterval +
                    "s. This build is diagnostic/prototype only; it is not connected to the game's firing/slot lifecycle.");
            }
            catch (Exception ex)
            {
                Logger.LogError("TWIN REQUIEM startup failed: " + ex);
                if (harmony != null)
                {
                    try { harmony.UnpatchSelf(); } catch (Exception cleanupEx) { Logger.LogWarning("Harmony cleanup failed: " + cleanupEx.Message); }
                    harmony = null;
                }
            }
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

    internal static class RequiemPalette
    {
        private static readonly Color RequiemCrimson = new Color32(104, 19, 34, 255);
        private static readonly Color RequiemPurple = new Color32(48, 25, 76, 255);
        private static readonly Color DirgeCrimson = new Color32(73, 13, 25, 255);
        private static readonly Color DirgePurple = new Color32(35, 20, 53, 255);
        private static readonly Color CrescendoCrimson = new Color32(125, 22, 42, 255);
        private static readonly Color CrescendoPurple = new Color32(66, 31, 103, 255);
        private static readonly Color EncoreCrimson = new Color32(49, 8, 20, 255);
        private static readonly Color EncorePurple = new Color32(25, 12, 39, 255);

        internal static void TintStockRevolver(object instance)
        {
            if (instance == null || Plugin.Instance == null ||
                Plugin.ApplyVisualTheme == null || !Plugin.ApplyVisualTheme.Value)
                return;

            Component component = instance as Component;
            if (component == null)
                return;

            Renderer[] renderers = component.GetComponentsInChildren<Renderer>(true);
            if (renderers == null || renderers.Length == 0)
                return;

            string variant = Plugin.Variant != null ? Plugin.Variant.Value.Trim() : "Requiem";
            Color crimson = RequiemCrimson;
            Color purple = RequiemPurple;

            if (string.Equals(variant, "Dirge", StringComparison.OrdinalIgnoreCase))
            {
                crimson = DirgeCrimson;
                purple = DirgePurple;
            }
            else if (string.Equals(variant, "Crescendo", StringComparison.OrdinalIgnoreCase))
            {
                crimson = CrescendoCrimson;
                purple = CrescendoPurple;
            }
            else if (string.Equals(variant, "Encore", StringComparison.OrdinalIgnoreCase))
            {
                crimson = EncoreCrimson;
                purple = EncorePurple;
            }

            for (int i = 0; i < renderers.Length; i++)
            {
                Renderer renderer = renderers[i];
                if (renderer == null)
                    continue;
                try
                {
                    Material material = renderer.material;
                    if (material != null && material.HasProperty("_Color"))
                        material.color = (i % 2 == 0) ? crimson : purple;
                }
                catch (Exception ex)
                {
                    if (Plugin.Instance != null)
                        Plugin.Instance.Logger.LogDebug("Skipping an unsupported Revolver material: " + ex.Message);
                }
            }
        }
    }

    [HarmonyPatch]
    internal static class StockRevolverVisualPreview
    {
        private static MethodBase TargetMethod()
        {
            Type revolver = AccessTools.TypeByName("Revolver");
            return revolver == null ? null : AccessTools.Method(revolver, "Start");
        }

        private static void Postfix(object __instance)
        {
            RequiemPalette.TintStockRevolver(__instance);
        }
    }
}
