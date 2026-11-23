package com.vrflex.quest.integration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Contract for optional, game-specific VRFlex adapters.
 *
 * Adapters are opt-in and must target a documented game/plugin interface.
 * This API deliberately does not expose filesystem, shell, root, or system
 * tracking mutation operations.
 */
public interface GameIntegration {
    Descriptor descriptor();

    /** Called only after the host has verified the game/package version. */
    void onProfileActivated(ProfileSettings settings, AppLocalControls controls);

    final class Descriptor {
        public final String id;
        public final String displayName;
        public final String packageName;
        public final int adapterVersion;
        public final int minGameVersion;
        public final int maxGameVersion;
        public final Set<Capability> capabilities;

        public Descriptor(String id, String displayName, String packageName,
                          int adapterVersion, int minGameVersion, int maxGameVersion,
                          Set<Capability> capabilities) {
            if (id == null || id.trim().isEmpty()
                    || displayName == null || displayName.trim().isEmpty()
                    || packageName == null || packageName.trim().isEmpty()
                    || adapterVersion < 1 || minGameVersion < 0
                    || maxGameVersion < minGameVersion) {
                throw new IllegalArgumentException("Invalid integration descriptor");
            }
            this.id = id;
            this.displayName = displayName;
            this.packageName = packageName;
            this.adapterVersion = adapterVersion;
            this.minGameVersion = minGameVersion;
            this.maxGameVersion = maxGameVersion;
            EnumSet<Capability> copy = capabilities == null || capabilities.isEmpty()
                    ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(capabilities);
            this.capabilities = Collections.unmodifiableSet(copy);
        }

        public boolean supportsGameVersion(int versionCode) {
            return versionCode >= minGameVersion && versionCode <= maxGameVersion;
        }
    }

    enum Capability {
        PROFILE_SETTINGS,
        APP_LOCAL_TRANSFORMS,
        TRIGGER_ACTIONS,
        FLOATING_OVERLAY
    }

    /** Immutable snapshot of the user's saved profile values. */
    final class ProfileSettings {
        public final boolean enabled;
        public final boolean sittingPreference;
        public final int forwardOffsetCm;
        public final int movementSpeedPercent;
        public final int leftArmPercent;
        public final int rightArmPercent;

        public ProfileSettings(boolean enabled, boolean sittingPreference,
                               int forwardOffsetCm, int movementSpeedPercent,
                               int leftArmPercent, int rightArmPercent) {
            this.enabled = enabled;
            this.sittingPreference = sittingPreference;
            this.forwardOffsetCm = clamp(forwardOffsetCm, -100, 100);
            this.movementSpeedPercent = clamp(movementSpeedPercent, 1, 100);
            this.leftArmPercent = clamp(leftArmPercent, -50, 50);
            this.rightArmPercent = clamp(rightArmPercent, -50, 50);
        }
    }

    /**
     * Narrow host API: adapters may change presentation inside the integrated
     * game only. It intentionally has no method for changing Quest-wide tracking.
     */
    interface AppLocalControls {
        boolean isCapabilityAvailable(Capability capability);
        void setAppLocalForwardOffset(float centimeters);
        void setAppLocalArmOffsets(float leftPercent, float rightPercent);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
