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
    internal static class GabrielArmController
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
                Plugin.Log.LogInfo("Gabriel's Arm hook ready: custom blue-arm variation registered.");
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
                    Plugin.Log.LogInfo("Gabriel's Arm selected.");
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
            GameObject armObject = GetMemberValue(fists, "currentArmObject") as GameObject;
            if (armObject == null)
            {
                Plugin.Log.LogWarning("Gabriel's Arm: currentArmObject wasn't available; visual will retry on the next arm change.");
                return;
            }

            Transform holder = FindDeepChild(armObject.transform, "Holder (1)");
            Transform parent = holder != null ? holder : armObject.transform;
            armVisual = new GameObject("GabrielArm_WhiteGoldOverlay");
            armVisual.transform.SetParent(parent, false);
            armVisual.transform.localPosition = holder != null
                ? new Vector3(-0.02f, -0.015f, 0.025f)
                : new Vector3(0f, 0f, 0.08f);
            armVisual.transform.localRotation = Quaternion.identity;
            armVisual.transform.localScale = Vector3.one;

            // Compact gauntlet plates overlay the existing animated hand. They are separate
            // primitives so the game's own punch animation still drives the arm.
            MakePart("Silver Gauntlet", PrimitiveType.Cube, armVisual.transform,
                new Vector3(0f, 0f, 0f), new Vector3(0.18f, 0.16f, 0.13f),
                Quaternion.Euler(0f, 0f, -12f), Silver);
            MakePart("Gold Knuckle Guard", PrimitiveType.Cube, armVisual.transform,
                new Vector3(0f, 0.045f, 0.067f), new Vector3(0.17f, 0.045f, 0.025f),
                Quaternion.Euler(0f, 0f, -8f), Gold);
            MakePart("Divine Center Stripe", PrimitiveType.Cube, armVisual.transform,
                new Vector3(0f, 0.002f, 0.082f), new Vector3(0.025f, 0.10f, 0.012f),
                Quaternion.identity, Light);
            MakePart("Outer Gold Plate", PrimitiveType.Cube, armVisual.transform,
                new Vector3(-0.075f, -0.005f, 0.035f), new Vector3(0.025f, 0.105f, 0.075f),
                Quaternion.Euler(0f, 0f, -12f), Gold);
            MakePart("Inner Gold Plate", PrimitiveType.Cube, armVisual.transform,
                new Vector3(0.075f, -0.005f, 0.035f), new Vector3(0.025f, 0.105f, 0.075f),
                Quaternion.Euler(0f, 0f, -12f), Gold);
        }

        private static Transform FindDeepChild(Transform root, string targetName)
        {
            if (root == null) return null;
            if (root.name == targetName) return root;
            for (int i = 0; i < root.childCount; i++)
            {
                Transform found = FindDeepChild(root.GetChild(i), targetName);
                if (found != null) return found;
            }
            return null;
        }

        private static GameObject MakePart(string name, PrimitiveType type, Transform parent,
            Vector3 position, Vector3 scale, Quaternion rotation, Color color)
        {
            GameObject part = GameObject.CreatePrimitive(type);
            part.name = "GabrielArm_" + name;
            part.transform.SetParent(parent, false);
            part.transform.localPosition = position;
            part.transform.localScale = scale;
            part.transform.localRotation = rotation;

            Collider collider = part.GetComponent<Collider>();
            if (collider != null) UnityEngine.Object.Destroy(collider);

            Renderer renderer = part.GetComponent<Renderer>();
            if (renderer != null)
            {
                Shader shader = Shader.Find("Universal Render Pipeline/Lit");
                if (shader == null) shader = Shader.Find("Standard");
                if (shader != null) renderer.material = new Material(shader);
                renderer.material.color = color;
                if (renderer.material.HasProperty("_EmissionColor"))
                    renderer.material.SetColor("_EmissionColor", color * 0.16f);
            }
            return part;
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
    internal static class GabrielArmCyclePatch
    {
        private static void Prefix(ref int orderNum)
        {
            GabrielArmController.OnArmChangePrefix(ref orderNum);
        }

        private static void Postfix(int orderNum, FistControl __instance)
        {
            GabrielArmController.OnArmChangePostfix(orderNum, __instance);
        }
    }

    [HarmonyPatch(typeof(Punch), "PunchStart")]
    internal static class GabrielArmPunchPatch
    {
        private static void Postfix(Punch __instance)
        {
            if (!GabrielArmController.IsEquipped || __instance == null)
                return;

            // Keep the base-game punch and hit logic intact. This hook is reserved for
            // arm-specific combo timing/effects after the actual arm cycle is validated.
        }
    }
}
