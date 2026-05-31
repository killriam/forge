package forge.game.simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import forge.game.Game;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Exports simulation statistics to JSON file.
 * Format: forge-simulation-stats v2.0.0
 */
public class SimulationStatsExporter {
    private static final Logger LOG = LoggerFactory.getLogger(SimulationStatsExporter.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    /**
     * Export simulation stats to JSON file.
     *
     * @param game Game instance with SimulationMetricsCollector attached
     * @param outputDir Directory to write JSON file
     * @return Created file or null if export failed
     */
    public static File exportToJson(Game game, File outputDir) {
        SimulationMetricsCollector collector = game.getSimulationMetricsCollector();

        if (collector == null) {
            LOG.debug("No simulation metrics collector attached to game");
            return null;
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        SimulationStats stats = collector.exportStats();

        String filename = String.format(
            "simulation_stats_%s.json",
            DATE_FORMAT.format(new Date())
        );

        File outputFile = new File(outputDir, filename);

        try (FileWriter writer = new FileWriter(outputFile)) {
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls() // Include null values for optional fields
                .create();

            gson.toJson(stats, writer);

            LOG.info("Simulation stats exported: {}", outputFile.getAbsolutePath());
            return outputFile;

        } catch (IOException e) {
            LOG.error("Failed to export simulation stats to {}", outputFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Export simulation stats with custom filename.
     */
    public static File exportToJson(Game game, File outputDir, String filename) {
        SimulationMetricsCollector collector = game.getSimulationMetricsCollector();

        if (collector == null) {
            return null;
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        SimulationStats stats = collector.exportStats();
        File outputFile = new File(outputDir, filename);

        try (FileWriter writer = new FileWriter(outputFile)) {
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();

            gson.toJson(stats, writer);

            LOG.info("Simulation stats exported: {}", outputFile.getAbsolutePath());
            return outputFile;

        } catch (IOException e) {
            LOG.error("Failed to export simulation stats", e);
            return null;
        }
    }
}

