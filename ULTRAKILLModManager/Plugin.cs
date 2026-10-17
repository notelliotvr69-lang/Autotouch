using System;
using System.Linq;
using BepInEx;
using BepInEx.Bootstrap;
using UnityEngine;

namespace ULTRAKILLModManager
{
    [BepInPlugin("notelliotvr69.modmanager", "ULTRAKILL Mod Manager", "0.1.2")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private bool visible;
        private Rect window = new Rect(30, 30, 540, 460);
        private Vector2 scroll;
        private string filter = "";

        private void Awake()
        {
            Logger.LogInfo("Mod Manager initialized. Press F6 or F7 in-game to toggle the window.");
        }

        private void Update()
        {
            try
            {
                if (Input.GetKeyDown(KeyCode.F6) || Input.GetKeyDown(KeyCode.F7))
                {
                    visible = !visible;
                    Logger.LogInfo("Mod Manager window " + (visible ? "opened" : "closed") + " by hotkey.");
                }
            }
            catch (Exception ex)
            {
                Logger.LogError("Hotkey handling failed: " + ex);
            }
        }

        private void OnGUI()
        {
            if (!visible) return;

            try
            {
                window = GUI.Window(194517, window, DrawWindow, "ULTRAKILL MOD MANAGER 0.1.2");
            }
            catch (Exception ex)
            {
                Logger.LogError("Drawing the Mod Manager window failed: " + ex);
                visible = false;
            }
        }

        private void DrawWindow(int id)
        {
            GUILayout.BeginVertical();
            GUILayout.Label("Loaded BepInEx plugins: " + Chainloader.PluginInfos.Count);
            GUILayout.Label("Press F6 or F7 to close. Restart the game to apply plugin changes.");
            filter = GUILayout.TextField(filter ?? "", GUILayout.Height(28));
            GUILayout.Space(6);
            scroll = GUILayout.BeginScrollView(scroll);
            foreach (var entry in Chainloader.PluginInfos.OrderBy(p => p.Value.Metadata.Name))
            {
                var info = entry.Value;
                string name = info.Metadata.Name ?? info.Metadata.GUID;
                if (!string.IsNullOrEmpty(filter) &&
                    name.IndexOf(filter, StringComparison.OrdinalIgnoreCase) < 0 &&
                    info.Metadata.GUID.IndexOf(filter, StringComparison.OrdinalIgnoreCase) < 0)
                    continue;

                GUILayout.BeginVertical(GUI.skin.box);
                GUILayout.Label(name);
                GUILayout.Label("GUID: " + info.Metadata.GUID);
                GUILayout.Label("Version: " + info.Metadata.Version);
                GUILayout.Label("Assembly: " + info.Instance.GetType().Assembly.GetName().Name + ".dll");
                GUILayout.EndVertical();
            }
            GUILayout.EndScrollView();
            GUILayout.EndVertical();
            GUI.DragWindow(new Rect(0, 0, 10000, 26));
        }
    }
}
