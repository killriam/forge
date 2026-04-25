# Problem Summary: Forge Build and Execution Issues

## Observed Behavior
When running Forge via the `start_forge_console.bat` script or manually executing the jar file, the application fails to start and throws the following exception:

```
Exception: java.lang.ExceptionInInitializerError thrown from the UncaughtExceptionHandler in thread "main"
java.lang.ExceptionInInitializerError
        at forge.Singletons.initializeOnce(Singletons.java:52)
        at forge.view.Main.main(Main.java:68)
Caused by: java.lang.RuntimeException: Cannot find default skin at D:\Daten\SoftwareProjekte\Forge\forge\..\forge-gui\res\skins\default\bg_splash.png
        at forge.toolbox.FSkin.loadLight(FSkin.java:1193)
        at forge.toolbox.FSkin.loadLight(FSkin.java:1195)
        at forge.view.SplashFrame.<init>(SplashFrame.java:81)
        at forge.view.FView.<init>(FView.java:88)
        at forge.view.FView.<clinit>(FView.java:60)
        ... 2 more
```

## Key Observations
1. **Skin Resource Missing**: The error indicates that the application cannot locate the default skin resource `bg_splash.png`.
2. **Build vs Run Configuration**: The application runs successfully when started via the IDE's run configuration but fails when built and executed manually.
3. **Potential Build Differences**: There may be discrepancies between the build process invoked by the IDE and the manual build process.
4. **Java Options Impact**: The IDE's run configuration includes additional Java options such as `--add-opens` flags, which might affect runtime behavior.

## Steps to Reproduce
1. Build the project manually using Maven:
   ```
   mvn clean install
   ```
2. Run the application using the `start_forge_console.bat` script:
   ```
   start_forge_console.bat
   ```
3. Observe the exception during startup.

## Hypotheses
- **Resource Path Issue**: The relative path to the `res/skins/default/bg_splash.png` file might not be resolved correctly in the manual build.
- **Incomplete Build**: The manual build process might not include all necessary resources.
- **Java Options**: Missing JVM options in the manual execution might lead to runtime issues.

## Next Steps
1. **Verify Resource Inclusion**: Check if the `res/skins/default/bg_splash.png` file is included in the built jar.
2. **Compare Build Processes**: Analyze the differences between the IDE's build/run configuration and the manual Maven build.
3. **Add Debug Logs**: Insert debug messages in the `FSkin` class to trace the resource loading process.
4. **Test with JVM Options**: Run the jar with the same JVM options as the IDE's run configuration.

## Temporary Workaround
Use the IDE's run configuration to execute the application until the root cause is identified and resolved.

---

## Resolution (2026-03-30)

### Root Cause

`GuiDesktop.getAssetsDir()` returned the hardcoded path `"../forge-gui/"` only when the version string contained `"git"`. For SNAPSHOT builds (version string `2.0.12-SNAPSHOT-*`), it fell through to `""`, causing Forge to look for skin resources in the current working directory instead of the `forge-gui/res/` subdirectory.

Additionally, three symbols referenced by custom branch code were missing from upstream classes, preventing compilation.

### Fixes Applied

**1. `forge-gui-desktop/src/main/java/forge/GuiDesktop.java`**

Replaced the hardcoded `"../forge-gui/"` return value with path probing that works regardless of the working directory at launch time. The method now also covers SNAPSHOT builds (not just "git" builds):

```java
public String getAssetsDir() {
    final String version = BuildInfo.getVersionString();
    final boolean isDevBuild = StringUtils.containsIgnoreCase(version, "git")
            || StringUtils.containsIgnoreCase(version, "SNAPSHOT");
    if (isDevBuild) {
        if (new File("../forge-gui/res").exists()) return "../forge-gui/";  // IDE (cwd = forge-gui-desktop/)
        if (new File("forge-gui/res").exists())   return "forge-gui/";      // bat (cwd = project root)
    }
    return "";
}
```

**2. `start_forge_console.bat`**

Changed working directory to `forge-gui-desktop/` before launching the jar (matching the IDE convention). Added dynamic jar filename resolution and a clear error message if the build is missing.

**3. `forge-game/pom.xml`**

Added missing `com.google.code.gson` dependency required by `ReplayLibraryReorderer`.

**4. `forge-game/src/main/java/forge/game/GameRules.java`**

Added missing `replayLogPath` field with getter `getReplayLogPath()` and setter `setReplayLogPath()`, which was called in `GameAction.java` but not yet declared.

**5. `forge-game/src/main/java/forge/game/player/RegisteredPlayer.java`**

Added missing `decklistConfigPath` field with getter `getDecklistConfigPath()` and setter `setDecklistConfigPath()`, which was called in `SimulateMatch.java` but not yet declared.

**6. `forge-gui-desktop/src/main/java/forge/gui/framework/EDocID.java`**

Added missing `HOME_REPLAY` enum entry and the corresponding `VSubmenuReplay` import, required by `VSubmenuReplay` and `CSubmenuReplay`.

### Verification

After applying all fixes, `mvn clean install -DskipTests` completes successfully. Launching the jar from `forge-gui-desktop/` (as the updated bat script does) starts the application without the `ExceptionInInitializerError`.
