using System;
using System.Collections;
using System.Reflection;
using BepInEx;
using BepInEx.Logging;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "Arsenal Expansion 0.4.0 - GODSPLITTER", "0.4.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static ManualLogSource Log;
        private bool addingWeapons;
        private bool weaponsAdded;

        private void Awake()
        {
            Log = Logger;
            Log.LogInfo("Arsenal Expansion 0.4.0 loaded. GODSPLITTER prototype will be added during gameplay.");
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
                if (guns == null) continue;

                addingWeapons = true;
                try
                {
                    // Register GODSPLITTER independently: optional Museum prefab failures must not block it.
                    AddGodsplitter(guns);
                    guns.UpdateWeaponList();
                    Log.LogInfo("GODSPLITTER registration attempted. slot6 count: " + guns.slot6.Count);

                    TryAddMuseumWeapon("Assets/Prefabs/Weapons/Old/Beamcutter Blue.prefab", "Beamcutter Blue", guns);
                    TryAddMuseumWeapon("Assets/Prefabs/Weapons/Old/Black Hole Cannon.prefab", "Black Hole Cannon", guns);

                    guns.UpdateWeaponList();
                    weaponsAdded = true;
                    Log.LogInfo("Weapon registration pass completed.");
                }
                catch (Exception ex)
                {
                    Log.LogError("GODSPLITTER registration failed; will retry: " + ex);
                }
                finally
                {
                    addingWeapons = false;
                }
            }
        }

        private static void TryAddMuseumWeapon(string prefabPath, string weaponName, GunControl guns)
        {
            try
            {
                AddWeapon(prefabPath, weaponName, guns);
                Plugin.Log.LogInfo("Museum weapon registered or already present: " + weaponName);
            }
            catch (Exception ex)
            {
                Plugin.Log.LogWarning("Optional Museum weapon skipped (" + weaponName + "): " + ex.Message);
            }
        }

        private static void AddGodsplitter(GunControl guns)
        {
            foreach (Transform child in guns.transform)
            {
                if (child != null && child.name == "GODSPLITTER")
                {
                    GameObject existing = child.gameObject;
                    if (existing.GetComponent<GodsplitterWeapon>() == null)
                        existing.AddComponent<GodsplitterWeapon>();
                    if (!guns.slot6.Contains(existing)) guns.slot6.Add(existing);
                    Log.LogInfo("Found existing GODSPLITTER object and ensured it is in slot6.");
                    return;
                }
            }

            GameObject sword = new GameObject("GODSPLITTER");
            sword.transform.SetParent(guns.transform, false);
            sword.AddComponent<GodsplitterWeapon>();
            guns.slot6.Add(sword);
            Log.LogInfo("Created GODSPLITTER object and added it to slot6.");
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
