using System;
using System.Collections;
using BepInEx;
using HarmonyLib;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "Arsenal Expansion - Gabriel's Arm", "0.5.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static BepInEx.Logging.ManualLogSource Log;
        private Harmony harmony;

        private void Awake()
        {
            Log = Logger;
            harmony = new Harmony("notelliotvr69.arsenalexpansion.gabrielsarm");
            harmony.PatchAll();
            Log.LogInfo("Arsenal Expansion loaded. Gabriel's Arm prototype enabled; weapon work is intentionally paused.");
            StartCoroutine(WaitForGameplay());
        }

        private IEnumerator WaitForGameplay()
        {
            while (true)
            {
                yield return new WaitForSeconds(1f);
                if (SceneManager.GetActiveScene().name == "Main Menu" ||
                    SceneManager.GetActiveScene().name == "Intro")
                    continue;

                FistControl fists = MonoSingleton<FistControl>.Instance;
                if (fists != null)
                {
                    GabrielArmController.TryInitialize();
                    yield break;
                }
            }
        }

        private void OnDestroy()
        {
            if (harmony != null)
                harmony.UnpatchSelf();
            GabrielArmController.Cleanup();
        }
    }
}
