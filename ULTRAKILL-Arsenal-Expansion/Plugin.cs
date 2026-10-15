using BepInEx;
using BepInEx.Logging;

namespace ArsenalExpansion
{
    [BepInPlugin("notelliotvr69.arsenalexpansion", "Arsenal Expansion", "0.1.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        internal static ManualLogSource Log;

        private void Awake()
        {
            Log = Logger;
            Log.LogInfo("Arsenal Expansion loaded. Weapon implementations are not added yet.");
        }
    }
}
