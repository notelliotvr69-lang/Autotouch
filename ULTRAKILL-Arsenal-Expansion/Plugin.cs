using System;
using System.Collections;
using System.Reflection;
using BepInEx;
using BepInEx.Logging;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion", "Arsenal Expansion", "0.2.1")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static ManualLogSource Log;
        private bool addingWeapons;

        private void Awake()
        {
            Log = Logger;
            SceneManager.sceneLoaded += OnSceneLoaded;
            Log.LogInfo("Arsenal Expansion loaded. Museum weapons will be added to the extra weapon slot; no Cheats-menu registration is used.");
        }

        private void OnDestroy()
        {
            SceneManager.sceneLoaded -= OnSceneLoaded;
        }

        private void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            if (scene.name == "Main Menu" || scene.name == "Intro" || addingWeapons)
                return;

            StartCoroutine(AddMuseumWeaponsWhenReady());
        }

        private IEnumerator AddMuseumWeaponsWhenReady()
        {
            if (addingWeapons)
                yield break;

            addingWeapons = true;
            int framesWaited = 0;
            while (MonoSingleton<GunControl>.Instance == null && framesWaited < 300)
            {
                framesWaited++;
                yield return null;
            }

            GunControl guns = MonoSingleton<GunControl>.Instance;
            if (guns == null)
            {
                Log.LogWarning("GunControl did not initialize; museum weapons were not added in this scene.");
                addingWeapons = false;
                yield break;
            }

            try
            {
                AddWeapon("Assets/Prefabs/Weapons/Old/Beamcutter Blue.prefab", "Beamcutter Blue", guns);
                AddWeapon("Assets/Prefabs/Weapons/Old/Black Hole Cannon.prefab", "Black Hole Cannon", guns);
                guns.UpdateWeaponList();
                Log.LogInfo("Added Beamcutter and Black Hole Cannon to extra weapon slot.");
            }
            catch (Exception ex)
            {
                Log.LogError("Could not add museum weapons. Check the game version and prefab paths: " + ex);
            }

            addingWeapons = false;
        }

        private static void AddWeapon(string prefabPath, string weaponName, GunControl guns)
        {
            foreach (Transform child in guns.transform)
            {
                if (child != null && child.name.Contains(weaponName))
                    return;
            }

            // Resolve AssetHelper by reflection so the build does not need a compile-time
            // reference to Unity.Addressables, which is only used by the helper's API.
            Type helperType = typeof(GunControl).Assembly.GetType("AssetHelper");
            if (helperType == null)
                throw new InvalidOperationException("Could not find AssetHelper in Assembly-CSharp.");

            MethodInfo loadMethod = helperType.GetMethod(
                "LoadPrefab",
                BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static,
                null,
                new[] { typeof(string) },
                null);

            if (loadMethod == null)
                throw new InvalidOperationException("Could not find AssetHelper.LoadPrefab(string).");

            GameObject prefab = loadMethod.Invoke(null, new object[] { prefabPath }) as GameObject;
            if (prefab == null)
                throw new InvalidOperationException("Could not load prefab: " + prefabPath);

            GameObject weapon = UnityEngine.Object.Instantiate(prefab, guns.transform);
            weapon.name = prefab.name;
            if (!guns.slot6.Contains(weapon))
                guns.slot6.Add(weapon);
        }
    }
}