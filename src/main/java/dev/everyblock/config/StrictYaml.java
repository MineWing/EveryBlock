package dev.everyblock.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Loads into a fresh document and propagates failures instead of installing defaults. */
public final class StrictYaml {
    private StrictYaml() {
    }

    public static YamlConfiguration load(File file) {
        YamlConfiguration prepared = new YamlConfiguration();
        try {
            prepared.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Cannot load " + file.getName() + ": "
                    + exception.getMessage(), exception);
        }
        return prepared;
    }
}
