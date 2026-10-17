using System;
using TwinRequiem;

internal static class Program
{
    private static int assertions;

    private static void Main()
    {
        TestVariantParsingAndTuning();
        TestAlternatingHandsAndCadence();
        TestEncoreChallengeRules();

        Console.WriteLine("TWIN REQUIEM mechanics tests passed (" + assertions + " assertions).");
    }

    private static void TestVariantParsingAndTuning()
    {
        Equal(5, RequiemRules.TargetWeaponSlot, "Target slot remains 5");
        Equal(RequiemVariant.Requiem, RequiemRules.ParseVariant("unknown"), "Unknown variant falls back to Requiem");
        Equal(RequiemVariant.Crescendo, RequiemRules.ParseVariant("crescendo"), "Variant parsing ignores case");

        VariantTuning baseTuning = RequiemRules.GetTuning(RequiemVariant.Requiem);
        VariantTuning dirge = RequiemRules.GetTuning(RequiemVariant.Dirge);
        VariantTuning crescendo = RequiemRules.GetTuning(RequiemVariant.Crescendo);
        Check(dirge.ShotInterval > baseTuning.ShotInterval, "Dirge fires slower than Requiem");
        Check(dirge.DamageMultiplier > baseTuning.DamageMultiplier, "Dirge has heavier damage tuning");
        Check(crescendo.ShotInterval < baseTuning.ShotInterval, "Crescendo fires faster than Requiem");
        Check(crescendo.SpreadMultiplier < 1f, "Crescendo tightens spread");
    }

    private static void TestAlternatingHandsAndCadence()
    {
        var sequencer = new DualShotSequencer();
        sequencer.Reset(10f);

        RequiemHand hand;
        Check(sequencer.TryConsumeShot(10f, 0.32f, out hand), "First shot can fire immediately");
        Equal(RequiemHand.Left, hand, "First shot uses left hand");
        Check(!sequencer.TryConsumeShot(10.1f, 0.32f, out hand), "Cadence blocks an early shot");
        Check(sequencer.TryConsumeShot(10.32f, 0.32f, out hand), "Second shot fires after interval");
        Equal(RequiemHand.Right, hand, "Second shot uses right hand");
        Check(sequencer.TryConsumeShot(10.64f, 0.32f, out hand), "Third shot fires after interval");
        Equal(RequiemHand.Left, hand, "Third shot returns to left hand");
    }

    private static void TestEncoreChallengeRules()
    {
        var tracker = new EncoreChallengeTracker();
        tracker.BeginLevel();
        Check(!tracker.QualifiesForUnlock(true), "Cannot unlock Encore without a Twin Requiem attack");

        tracker.RecordAttack(AttackSource.TwinRequiem);
        Check(!tracker.QualifiesForUnlock(false), "Encore requires level completion");
        Check(tracker.QualifiesForUnlock(true), "Twin Requiem-only completed level qualifies");

        tracker.BeginLevel();
        tracker.RecordAttack(AttackSource.TwinRequiem);
        tracker.RecordAttack(AttackSource.OtherWeapon);
        Check(tracker.IsInvalidated, "Other weapon attack invalidates challenge");
        Check(!tracker.QualifiesForUnlock(true), "Invalidated run cannot unlock Encore");

        tracker.BeginLevel();
        tracker.RecordAttack(AttackSource.TwinRequiem);
        tracker.RecordAttack(AttackSource.Punch);
        Check(!tracker.QualifiesForUnlock(true), "Punching invalidates challenge");
    }

    private static void Check(bool condition, string message)
    {
        assertions++;
        if (!condition)
            throw new Exception("FAIL: " + message);
    }

    private static void Equal<T>(T expected, T actual, string message)
    {
        assertions++;
        if (!Equals(expected, actual))
            throw new Exception("FAIL: " + message + " (expected " + expected + ", got " + actual + ")");
    }
}
