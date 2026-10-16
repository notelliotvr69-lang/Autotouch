using System;
using System.Collections;
using BepInEx;
using HarmonyLib;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion030", "Arsenal Expansion - GODSPLITTER Arm", "0.5.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static BepInEx.Logging.ManualLogSource Log;
        private Harmony harmony;

        private void Awake()
        {
            Log = Logger;
            harmony = new Harmony("notelliotvr69.arsenalexpansion.godsplitterarm");
            harmony.PatchAll();
            Log.LogInfo("Arsenal Expansion loaded. GODSPLITTER arm-slot prototype enabled; actual Gabriel arm model is not attached yet.");
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
                    GodsplitterArmController.TryInitialize();
                    yield break;
                }
            }
        }

        private void OnDestroy()
        {
            if (harmony != null)
                harmony.UnpatchSelf();
            GodsplitterArmController.Cleanup();
        }
    }
}
