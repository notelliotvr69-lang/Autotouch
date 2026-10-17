using System;

namespace TwinRequiem
{
    // Pure gameplay rules for the custom weapon. Kept independent of Unity so these
    // mechanics can be tested before wiring them into ULTRAKILL's weapon lifecycle.
    internal enum RequiemVariant
    {
        Requiem,
        Dirge,
        Crescendo,
        Encore
    }

    internal enum RequiemHand
    {
        Left,
        Right
    }

    internal enum AttackSource
    {
        TwinRequiem,
        OtherWeapon,
        Punch
    }

    internal struct VariantTuning
    {
        internal readonly float ShotInterval;
        internal readonly float DamageMultiplier;
        internal readonly float SpreadMultiplier;

        internal VariantTuning(float shotInterval, float damageMultiplier, float spreadMultiplier)
        {
            ShotInterval = shotInterval;
            DamageMultiplier = damageMultiplier;
            SpreadMultiplier = spreadMultiplier;
        }
    }

    internal static class RequiemRules
    {
        internal const int TargetWeaponSlot = 5;

        internal static RequiemVariant ParseVariant(string value)
        {
            RequiemVariant parsed;
            return Enum.TryParse(value, true, out parsed) ? parsed : RequiemVariant.Requiem;
        }

        internal static VariantTuning GetTuning(RequiemVariant variant)
        {
            switch (variant)
            {
                case RequiemVariant.Dirge:
                    return new VariantTuning(0.48f, 1.45f, 0.80f);
                case RequiemVariant.Crescendo:
                    return new VariantTuning(0.19f, 0.78f, 0.72f);
                case RequiemVariant.Encore:
                    return new VariantTuning(0.24f, 1.10f, 0.55f);
                default:
                    return new VariantTuning(0.32f, 1.00f, 1.00f);
            }
        }
    }

    // Owns cadence and alternating-hand state. The host weapon integration must
    // call TryFire when the player requests a shot and only consume a shot if it
    // actually creates a projectile successfully.
    internal sealed class DualShotSequencer
    {
        private bool nextIsLeft = true;
        private float nextShotTime;

        internal RequiemHand NextHand
        {
            get { return nextIsLeft ? RequiemHand.Left : RequiemHand.Right; }
        }

        internal bool CanFire(float gameTime)
        {
            return gameTime >= nextShotTime;
        }

        internal bool TryConsumeShot(float gameTime, float shotInterval, out RequiemHand hand)
        {
            hand = NextHand;
            if (!CanFire(gameTime))
                return false;

            nextShotTime = gameTime + Math.Max(0.01f, shotInterval);
            nextIsLeft = !nextIsLeft;
            return true;
        }

        internal void Reset(float gameTime)
        {
            nextIsLeft = true;
            nextShotTime = gameTime;
        }
    }

    // Tracks the secret Encore level challenge. Weapon switching is allowed;
    // any non-Twin-Requiem attack invalidates the current level attempt.
    internal sealed class EncoreChallengeTracker
    {
        private bool invalidated;
        private bool usedTwinRequiemAttack;

        internal bool IsInvalidated { get { return invalidated; } }
        internal bool HasQualifiedAttack { get { return usedTwinRequiemAttack; } }

        internal void RecordAttack(AttackSource source)
        {
            if (source == AttackSource.TwinRequiem)
                usedTwinRequiemAttack = true;
            else
                invalidated = true;
        }

        internal bool QualifiesForUnlock(bool levelCompleted)
        {
            return levelCompleted && usedTwinRequiemAttack && !invalidated;
        }

        internal void BeginLevel()
        {
            invalidated = false;
            usedTwinRequiemAttack = false;
        }
    }
}
