# AutoTouch

AutoTouch is a small native Android app for running user-configured tap or swipe routines. It uses Android's accessibility gesture API and a movable floating Start/Stop controller. No screen content is read or collected.

## Build

Install Android SDK Platform 35, then run:

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The repository intentionally does not commit the binary Gradle wrapper JAR. Install Gradle 8.9
locally, or use the GitHub Actions build, which provisions that Gradle version automatically.

## Use

1. Open AutoTouch and grant **display over other apps** permission.
2. Open Accessibility settings and explicitly enable **AutoTouch gesture service**.
3. Configure a tap or swipe, interval, and finite repetition count, then save it.
4. Start the floating controller. Use its **Start** and **Stop** buttons to control the routine.

Coordinates use physical screen pixels. Automation is intentionally finite, local, and user initiated. Only automate apps and actions you are authorized to control.
