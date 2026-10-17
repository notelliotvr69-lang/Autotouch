using System;
using System.Linq;
using BepInEx;
using BepInEx.Bootstrap;
using UnityEngine;

namespace ULTRAKILLModManager
{
    [BepInPlugin("notelliotvr69.modmanager", "ULTRAKILL Mod Manager", "0.1.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private bool visible;
        private Rect window = new Rect(30, 30, 540, 460);
        private Vector2 scroll;
        private string filter = "";
        private GUIStyle titleStyle;
        private GUIStyle rowStyle;

        private void Update()
        {
            if (Input.GetKeyDown(KeyCode.F6))
                visible = !visible;
        }

        private void OnGUI()
        {
            if (!visible) return;
            if (titleStyle == null)
            {
                titleStyle = new GUIStyle(GUI.skin.label) { fontSize = 22, fontStyle = FontStyle.Bold };
                rowStyle = new GUIStyle(GUI.skin.label) { wordWrap = true };
            }
            window = GUI.Window(194517, window, DrawWindow, "ULTRAKILL MOD MANAGER");
        }

        private void DrawWindow(int id)
        {
            GUILayout.BeginVertical();
            GUILayout.Label("Loaded BepInEx plugins: " + Chainloader.PluginInfos.Count);
            GUILayout.Label("F6 closes this window. Restart the game to apply plugin changes.");
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
                GUILayout.Label(name, titleStyle);
                GUILayout.Label("GUID: " + info.Metadata.GUID, rowStyle);
                GUILayout.Label("Version: " + info.Metadata.Version, rowStyle);
                GUILayout.Label("Assembly: " + info.Instance.GetType().Assembly.GetName().Name + ".dll", rowStyle);
                GUILayout.EndVertical();
            }
            GUILayout.EndScrollView();
            if (GUILayout.Button("Refresh list")) Repaint();
            GUILayout.EndVertical();
            GUI.DragWindow(new Rect(0, 0, 10000, 26));
        }
    }
}