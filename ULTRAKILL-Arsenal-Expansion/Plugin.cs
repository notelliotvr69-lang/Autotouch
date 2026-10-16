using System;
using System.Collections;
using BepInEx;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "Arsenal Expansion - GODSPLITTER", "0.4.1")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static BepInEx.Logging.ManualLogSource Log;
        private bool addingWeapon;
        private bool weaponAdded;

        private void Awake()
        {
            Log = Logger;
            Log.LogInfo("Arsenal Expansion loaded. GODSPLITTER-only build; Museum weapons are disabled.");
            StartCoroutine(WatchForGameplay());
        }

        private IEnumerator WatchForGameplay()
        {
            while (!weaponAdded)
            {
                yield return new WaitForSeconds(1f);
                string sceneName = SceneManager.GetActiveScene().name;
                if (sceneName == "Main Menu" || sceneName == "Intro" || addingWeapon)
                    continue;

                GunControl guns = MonoSingleton<GunControl>.Instance;
                if (guns == null) continue;

                addingWeapon = true;
                try
                {
                    AddGodsplitter(guns);
                    guns.UpdateWeaponList();
                    Log.LogInfo("GODSPLITTER registered in slot6. slot6 count: " + guns.slot6.Count);
                    weaponAdded = true;
                }
                catch (Exception ex)
                {
                    Log.LogError("GODSPLITTER registration failed; will retry: " + ex);
                }
                finally
                {
                    addingWeapon = false;
                }
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
    }
}
