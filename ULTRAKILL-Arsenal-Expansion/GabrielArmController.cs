using System;
using System.Collections.Generic;
using System.Reflection;
using HarmonyLib;
using UnityEngine;

namespace ArsenalExpansion
{
    /// <summary>
    /// First arm-slot integration pass. Uses the game's FistControl arm cycling and Punch
    /// lifecycle instead of registering the arm as a GunControl weapon.
    /// </summary>
    internal static class GodsplitterArmController
    {
        internal static bool IsEquipped { get; private set; }
        private static int currentVariation = -1;
        private const int ArmVariationCount = 1;
        private static GameObject armVisual;
        private static FistControl lastFistControl;
        private static readonly Color Gold = new Color(1f, 0.76f, 0.18f, 1f);
        private static readonly Color Silver = new Color(0.83f, 0.86f, 0.91f, 1f);
        private static readonly Color Light = new Color(1f, 0.97f, 0.78f, 1f);

        internal static void TryInitialize()
        {
            // Harmony's ArmChange hook is the registration point; this call is deliberately
            // idempotent and gives us a safe point to log that the game systems are present.
            if (MonoSingleton<FistControl>.Instance != null)
                Plugin.Log.LogInfo("GODSPLITTER arm-slot hook ready; actual Gabriel arm asset integration is pending.");
        }

        internal static void OnArmChangePrefix(ref int orderNum)
        {
            // Add one custom variation to the standard/blue arm cycle, following the same
            // cycling pattern used by the established Custom Arms proof of concept.
            if (orderNum == 1)
            {
                if (currentVariation + 1 < ArmVariationCount)
                    orderNum = 0;
                else
                    currentVariation = -2;
            }
            else if (orderNum == 0)
            {
                if (currentVariation + 1 < ArmVariationCount)
                    orderNum = 1;
                else
                    currentVariation = -2;
            }
        }

        internal static void OnArmChangePostfix(int orderNum, FistControl instance)
        {
            ClearVisual();
            lastFistControl = instance;

            if (orderNum == 0)
            {
                currentVariation++;
                if (currentVariation + 1 > ArmVariationCount)
                {
                    currentVariation = -1;
                    IsEquipped = false;
                    return;
                }

                if (currentVariation == 0)
                {
                    IsEquipped = true;
                    BuildVisual(instance);
                    SetFistIconColor(instance, Gold);
                    Plugin.Log.LogInfo("GODSPLITTER selected.");
                    return;
                }
            }
            else
            {
                currentVariation++;
                if (currentVariation + 1 > ArmVariationCount)
                {
                    currentVariation = -1;
                    IsEquipped = false;
                }
            }

            IsEquipped = false;
        }

        private static void BuildVisual(FistControl fists)
        {
            // Do not substitute Unity primitives or a generic gauntlet.
            // GODSPLITTER must use Gabriel's actual model/rig; that asset has not
            // yet been located and attached, so this intentionally creates no fake visual.
            Plugin.Log.LogWarning("GODSPLITTER selected, but Gabriel's actual arm model is not attached yet.");
        }

        private static void SetFistIconColor(FistControl fists, Color color)
        {
            object icon = GetMemberValue(fists, "fistIcon");
            if (icon == null) return;
            PropertyInfo colorProperty = icon.GetType().GetProperty("color",
                BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
            if (colorProperty != null && colorProperty.CanWrite && colorProperty.PropertyType == typeof(Color))
                colorProperty.SetValue(icon, color, null);
        }

        private static object GetMemberValue(object instance, string name)
        {
            if (instance == null) return null;
            Type type = instance.GetType();
            FieldInfo field = type.GetField(name, BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
            if (field != null) return field.GetValue(instance);
            PropertyInfo property = type.GetProperty(name, BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
            return property != null && property.CanRead ? property.GetValue(instance, null) : null;
        }

        private static void ClearVisual()
        {
            if (armVisual != null)
                UnityEngine.Object.Destroy(armVisual);
            armVisual = null;
        }

        internal static void Cleanup()
        {
            ClearVisual();
            IsEquipped = false;
            currentVariation = -1;
            lastFistControl = null;
        }
    }

    [HarmonyPatch(typeof(FistControl), nameof(FistControl.ArmChange))]
    internal static class GodsplitterArmCyclePatch
    {
        private static void Prefix(ref int orderNum)
        {
            GodsplitterArmController.OnArmChangePrefix(ref orderNum);
        }

        private static void Postfix(int orderNum, FistControl __instance)
        {
            GodsplitterArmController.OnArmChangePostfix(orderNum, __instance);
        }
    }

    [HarmonyPatch(typeof(Punch), "PunchStart")]
    internal static class GodsplitterArmPunchPatch
    {
        private static void Postfix(Punch __instance)
        {
            if (!GodsplitterArmController.IsEquipped || __instance == null)
                return;

            // Keep the base-game punch and hit logic intact. This hook is reserved for
            // arm-specific combo timing/effects after the actual arm cycle is validated.
        }
    }
}
