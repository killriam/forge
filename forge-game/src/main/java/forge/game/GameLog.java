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
package forge.game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Set;

import forge.game.log.ReplayNotationExporter;
import forge.game.player.Player;

/**
 * <p>
 * GameLog class.
 * 
 * @author Forge
 * @version $Id: GameLog.java 12297 2011-11-28 19:56:47Z slapshot5 $
 */
public class GameLog extends Observable implements Serializable {
    private static final long serialVersionUID = 6465283802022948827L;

    private final List<GameLogEntry> log = new ArrayList<>();

    private final transient GameLogFormatter formatter = new GameLogFormatter(this);

    /** Logging level:
     * 0 - Turn
     * 2 - Stack items
     * 3 - Poison Counters
     * 4 - Mana abilities
     * 6 - All Phase information
     * 7 - Analysis (includes zone changes and board state deltas)
     */

    public GameLog() {
    }

    public void add(final GameLogEntryType type, final String message) {
        add(new GameLogEntry(type, message));
    }

    void add(GameLogEntry entry) {
        log.add(entry);
        this.setChanged();
        this.notifyObservers();
    }

    /** All entries in chronological (insertion) order — note {@link #getLogEntries} returns newest-first. */
    public List<GameLogEntry> getAllEntries() {
        return new ArrayList<>(log);
    }

    /**
     * Gets the log entries below a certain level as a list.
     *
     * @param logLevel the log level
     * @return the log text
     */
    public List<GameLogEntry> getLogEntries(final GameLogEntryType logLevel) { // null to fetch all
        final List<GameLogEntry> result = new ArrayList<>();
    
        for (int i = log.size() - 1; i >= 0; i--) {
            GameLogEntry le = log.get(i);
            if (logLevel == null || le.type().compareTo(logLevel) <= 0) {
                result.add(le);
            }
        }
        return result;
    }

    public List<GameLogEntry> getLogEntriesForVerbosity(final GameLogVerbosity verbosity) {
        return getLogEntriesForTypes(verbosity.getIncludedTypes());
    }

    public List<GameLogEntry> getLogEntriesForTypes(final Set<GameLogEntryType> types) {
        final List<GameLogEntry> result = new ArrayList<>();
        for (int i = log.size() - 1; i >= 0; i--) {
            GameLogEntry le = log.get(i);
            if (types.contains(le.type())) {
                result.add(le);
            }
        }
        return result;
    }

    public List<GameLogEntry> getLogEntriesExact(final GameLogEntryType logLevel) { // null to fetch all
        final List<GameLogEntry> result = new ArrayList<>();
    
        for (int i = log.size() - 1; i >= 0; i--) {
            GameLogEntry le = log.get(i);
            if (logLevel == null || le.type().compareTo(logLevel) == 0) {
                result.add(le);
            }
        }
        return result;
    }

    public GameLogFormatter getEventVisitor() {
        return formatter;
    }

    /**
     * Enable JSON Replay Notation logging alongside text logging.
     * @param exporter The replay notation exporter to use
     */
    public void enableReplayNotation(ReplayNotationExporter exporter) {
        formatter.setReplayExporter(exporter);
    }

    /**
     * Get the replay notation exporter if enabled.
     * @return The exporter, or null if not enabled
     */
    public ReplayNotationExporter getReplayExporter() {
        return formatter.getReplayExporter();
    }

    /**
     * Place a player-defined learning marker / bookmark at the current game state.
     * <p>
     * Adds a visible {@link GameLogEntryType#INFORMATION} entry to the in-game log so
     * the player can see the bookmark in the Log panel, and — when Replay Notation
     * logging is active — also writes a {@code LEARNING_MARKER} event to the JSON log.
     *
     * @param player   The player placing the bookmark
     * @param label    Short description (may be empty but not null)
     * @param category One of the LEARNING_MARKER categories, e.g. {@code "general"}
     */
    public void logLearningMarker(final Player player, final String label, final String category) {
        final String displayLabel = (label == null || label.isEmpty()) ? "(no label)" : label;
        add(GameLogEntryType.INFORMATION,
                "[Bookmark] " + displayLabel + " \u2013 " + player.getName());
        final ReplayNotationExporter exporter = getReplayExporter();
        if (exporter != null) {
            exporter.logLearningMarker(player, label != null ? label : "", category);
        }
    }
}
