using BepInEx;
using BepInEx.Configuration;
using UnityEngine;

namespace TimeDilation
{
    [BepInPlugin("notelliotvr69.timedilation", "Time Dilation", "1.0.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        private ConfigEntry<KeyCode> toggleKey;
        private ConfigEntry<float> slowScale;
        private bool active;
        private float previousScale = 1f;

        private void Awake()
        {
            toggleKey = Config.Bind("Controls", "Toggle key", KeyCode.F6, "Toggle time dilation.");
            slowScale = Config.Bind("Mechanic", "Slow motion scale", 0.35f, "Time scale while active. Lower values are slower.");
            Logger.LogInfo("Time Dilation loaded. Press " + toggleKey.Value + " to toggle slow motion.");
        }

        private void Update()
        {
            if (Input.GetKeyDown(toggleKey.Value))
            {
                if (active) DisableSlowMotion();
                else EnableSlowMotion();
            }
        }

        private void EnableSlowMotion()
        {
            previousScale = Time.timeScale > 0f ? Time.timeScale : 1f;
            Time.timeScale = Mathf.Clamp(slowScale.Value, 0.05f, 1f);
            Time.fixedDeltaTime = 0.02f * Time.timeScale;
            active = true;
            Logger.LogInfo("Time Dilation enabled: timeScale=" + Time.timeScale);
        }

        private void DisableSlowMotion()
        {
            Time.timeScale = previousScale > 0f ? previousScale : 1f;
            Time.fixedDeltaTime = 0.02f * Time.timeScale;
            active = false;
            Logger.LogInfo("Time Dilation disabled.");
        }

        private void OnDestroy()
        {
            if (active) DisableSlowMotion();
        }

        private void OnApplicationQuit()
        {
            if (active) DisableSlowMotion();
        }
    }
}
