using System;
using System.Collections;
using System.Reflection;
using BepInEx;
using BepInEx.Logging;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "Arsenal Expansion 0.3.0", "0.3.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static ManualLogSource Log;
        private bool addingWeapons;
        private bool weaponsAdded;

        private void Awake()
        {
            Log = Logger;
            Log.LogInfo("Arsenal Expansion 0.3.0 loaded. Waiting for gameplay weapon manager.");
            StartCoroutine(WatchForGameplay());
        }

        private IEnumerator WatchForGameplay()
        {
            while (!weaponsAdded)
            {
                yield return new WaitForSeconds(1f);

                string sceneName = SceneManager.GetActiveScene().name;
                if (sceneName == "Main Menu" || sceneName == "Intro" || addingWeapons)
                    continue;

                GunControl guns = MonoSingleton<GunControl>.Instance;
                if (guns == null)
                    continue;

                addingWeapons = true;
                Log.LogInfo("Gameplay scene detected (" + sceneName + "); attempting to add museum weapons.");
                try
                {
                    AddWeapon("Assets/Prefabs/Weapons/Old/Beamcutter Blue.prefab", "Beamcutter Blue", guns);
                    AddWeapon("Assets/Prefabs/Weapons/Old/Black Hole Cannon.prefab", "Black Hole Cannon", guns);
                    guns.UpdateWeaponList();
                    weaponsAdded = true;
                    Log.LogInfo("Added Beamcutter and Black Hole Cannon to extra weapon slot.");
                }
                catch (Exception ex)
                {
                    Log.LogError("Could not add museum weapons; will retry: " + ex);
                }
                finally
                {
                    addingWeapons = false;
                }
            }
        }

        private static void AddWeapon(string prefabPath, string weaponName, GunControl guns)
        {
            foreach (Transform child in guns.transform)
            {
                if (child != null && child.name.Contains(weaponName))
                    return;
            }

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