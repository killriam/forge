/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.view;

import forge.GuiDesktop;
import forge.Singletons;
import forge.error.ExceptionHandler;
import forge.game.GameType;
import forge.gui.GuiBase;
import forge.gui.card.CardReaderExperiments;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.home.replay.CSubmenuReplay;
import forge.util.BuildInfo;
import io.sentry.Sentry;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Main class for Forge's swing application view.
 */
public final class Main {
    private static final class GuiLaunchOptions {
        private String playerOneDeck;
        private String playerTwoDeck;
        private GuiDeckFormat format = GuiDeckFormat.COMMANDER;
        private String pendingReplayPath;
        private boolean pendingReplayShuffle = false;
    }

    /**
     * Stores all format metadata as plain strings to avoid triggering
     * DeckType / GameType static initializers (which need Localizer)
     * before the GUI has set it up.
     */
    private enum GuiDeckFormat {
        COMMANDER("commander", "COMMANDER_DECK", FPref.COMMANDER_DECK_STATES, "Commander"),
        OATHBREAKER("oathbreaker", "OATHBREAKER_DECK", FPref.OATHBREAKER_DECK_STATES, "Oathbreaker"),
        TINYLEADERS("tinyleaders", "TINY_LEADERS_DECK", FPref.TINY_LEADER_DECK_STATES, "TinyLeaders"),
        BRAWL("brawl", "BRAWL_DECK", FPref.BRAWL_DECK_STATES, "Brawl"),
        CONSTRUCTED("constructed", "CUSTOM_DECK", FPref.CONSTRUCTED_DECK_STATES, null);

        private final String cliName;
        private final String deckTypeName;
        private final FPref[] prefKeys;
        /** GameType enum constant name, resolved lazily after Localizer is ready. */
        private final String variantName;

        GuiDeckFormat(final String cliName, final String deckTypeName, final FPref[] prefKeys, final String variantName) {
            this.cliName = cliName;
            this.deckTypeName = deckTypeName;
            this.prefKeys = prefKeys;
            this.variantName = variantName;
        }

        private static GuiDeckFormat fromCliValue(final String value) {
            final String normalized = value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (final GuiDeckFormat format : values()) {
                if (format.cliName.equals(normalized)) {
                    return format;
                }
            }
            return null;
        }
    }

    /**
     * Main entry point for Forge
     */
    public static void main(final String[] args) {
        Sentry.init(options -> {
            options.setEnableExternalConfiguration(true);
            options.setRelease(BuildInfo.getVersionString());
            options.setEnvironment(System.getProperty("os.name"));
            options.setTag("Java Version", System.getProperty("java.version"));
            options.setShutdownTimeoutMillis(5000);
            // these belong to sentry.properties, but somehow some OS/Zip tool discards it?
            if (options.getDsn() == null || options.getDsn().isEmpty())
                options.setDsn("https://87bc8d329e49441895502737c069067b@sentry.asgardsrealm.net/3");
        }, true);

        // HACK - temporary solution to "Comparison method violates it's general contract!" crash
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        //Turn off the Java 2D system's use of Direct3D to improve rendering speed (particularly when Full Screen)
        System.setProperty("sun.java2d.d3d", "false");

        //Turn on OpenGl acceleration to improve performance
        //System.setProperty("sun.java2d.opengl", "True");

        //setup GUI interface
        GuiBase.setInterface(new GuiDesktop());

        //install our error handler
        ExceptionHandler.registerErrorHandling();
        GuiBase.logHWInfo();

        // Start splash screen first, then data models, then controller.
        if (args.length == 0) {
            startGui();
            return;
        }

        // command line startup here
        String mode = args[0].toLowerCase(Locale.ROOT);

        if ("gui".equals(mode) || mode.startsWith("--")) {
            final String[] guiArgs = "gui".equals(mode) ? Arrays.copyOfRange(args, 1, args.length) : args;
            try {
                final GuiLaunchOptions options = parseGuiLaunchOptions(guiArgs);
                startGui(options);
            } catch (IllegalArgumentException ex) {
                System.out.println(ex.getMessage());
                printGuiUsage();
                System.exit(1);
            }
            return;
        }

        switch (mode) {
            case "sim":
                SimulateMatch.simulate(args);
                break;

            case "parse":
                CardReaderExperiments.parseAllCards(args);
                break;

            case "server":
                System.out.println("Dedicated server mode.\nNot implemented.");
                break;

            case "replay":
                if (args.length < 2) {
                    System.out.println("Error: Missing replay file path.\nUsage: java -jar <jar> replay <path-to-replay.json> [--shuffle|-s]");
                    System.exit(1);
                }
                final GuiLaunchOptions replayOptions = new GuiLaunchOptions();
                replayOptions.pendingReplayPath = args[1];
                if (args.length > 2 && ("--shuffle".equalsIgnoreCase(args[2]) || "-s".equalsIgnoreCase(args[2]))) {
                    replayOptions.pendingReplayShuffle = true;
                }
                startGui(replayOptions);
                return; // GUI stays alive on its own non-daemon thread; skip the System.exit(0) below

            default:
                System.out.println("Unknown mode.\nKnown modes are 'sim', 'parse', 'gui', 'replay'.");
                break;
        }

        System.exit(0);
    }

    private static void startGui() {
        startGui(null);
    }

    private static void startGui(final GuiLaunchOptions options) {
        Singletons.initializeOnce(true);

        // Apply CLI deck preselection after FModel/preferences are ready
        if (options != null) {
            applyGuiLaunchOptions(options);
        }

        // Controller can now step in and take over.
        Singletons.getControl().initialize();

        // Auto-launch a pending replay only now that the GUI, skins, and home screen
        // submenus (which reference skin icons in their constructors) have finished
        // initializing — referencing CSubmenuReplay any earlier than this crashes with
        // an NPE on an unloaded skin icon (e.g. IMG_BTN_START_OVER). Must run on the EDT: the
        // match screen's Swing components (e.g. PlayerDetailsPanel) assert this themselves
        // and throw IllegalStateException otherwise, same as every other caller of
        // startReplayFromPath, which are all invoked from Swing UI callbacks already on the EDT.
        if (options != null && options.pendingReplayPath != null) {
            final String replayPath = options.pendingReplayPath;
            final boolean shuffle = options.pendingReplayShuffle;
            SwingUtilities.invokeLater(() -> CSubmenuReplay.SINGLETON_INSTANCE.startReplayFromPath(replayPath, shuffle));
        }
    }

    private static GuiLaunchOptions parseGuiLaunchOptions(final String[] args) {
        final GuiLaunchOptions options = new GuiLaunchOptions();

        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            switch (arg) {
                case "--deck":
                case "--deck1":
                    options.playerOneDeck = requireOptionValue(args, ++i, arg);
                    break;
                case "--deck2":
                    options.playerTwoDeck = requireOptionValue(args, ++i, arg);
                    break;
                case "--format":
                    final String formatValue = requireOptionValue(args, ++i, arg);
                    final GuiDeckFormat format = GuiDeckFormat.fromCliValue(formatValue);
                    if (format == null) {
                        throw new IllegalArgumentException("Unknown GUI format: " + formatValue);
                    }
                    options.format = format;
                    break;
                case "-h":
                case "--help":
                    printGuiUsage();
                    System.exit(0);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown GUI option: " + arg);
            }
        }

        return options;
    }

    private static String requireOptionValue(final String[] args, final int valueIndex, final String option) {
        if (valueIndex >= args.length) {
            throw new IllegalArgumentException("Missing value for option: " + option);
        }
        return args[valueIndex];
    }

    private static void applyGuiLaunchOptions(final GuiLaunchOptions options) {
        if (options.playerOneDeck == null && options.playerTwoDeck == null) {
            return;
        }

        final ForgePreferences prefs = FModel.getPreferences();
        final String deckPrefix = options.format.deckTypeName + ";";

        if (options.playerOneDeck != null) {
            final String prefValue = deckPrefix + options.playerOneDeck;
            System.err.println("[DECK-PRESELECT] Writing P1 pref to " + options.format.prefKeys[0] + ": " + prefValue);
            prefs.setPref(options.format.prefKeys[0], prefValue);
        }
        if (options.playerTwoDeck != null) {
            final String prefValue = deckPrefix + options.playerTwoDeck;
            System.err.println("[DECK-PRESELECT] Writing P2 pref to " + options.format.prefKeys[1] + ": " + prefValue);
            prefs.setPref(options.format.prefKeys[1], prefValue);
        }

        // Resolve GameType by name now that Localizer is ready
        final Set<GameType> variants = EnumSet.noneOf(GameType.class);
        if (options.format.variantName != null) {
            for (final GameType gt : GameType.values()) {
                if (gt.name().equals(options.format.variantName)) {
                    variants.add(gt);
                    break;
                }
            }
        }
        // Set variant in memory only — lobby reads in-memory prefs; no save() here
        // to avoid accidentally overwriting user preferences mid-session.
        prefs.setGameType(FPref.UI_APPLIED_VARIANTS, variants);
    }

    private static void printGuiUsage() {
        System.out.println("GUI mode usage:");
        System.out.println("  java -jar <jar> gui [--format commander|oathbreaker|tinyleaders|brawl|constructed] [--deck <name>] [--deck2 <name>]");
        System.out.println("  java -jar <jar> --deck <name> [--deck2 <name>] [--format <format>]");
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        try {
            ExceptionHandler.unregisterErrorHandling();
        } finally {
            super.finalize();
        }
    }

    // disallow instantiation
    private Main() {
    }
}
