package dev.everyblock.config;

import dev.everyblock.message.MessageService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {
    @TempDir
    Path directory;

    private PluginSettings prepare(String yaml) throws Exception {
        Path config = directory.resolve("config.yml");
        Files.writeString(config, yaml);
        return ConfigService.prepare(config.toFile(), directory, Logger.getAnonymousLogger());
    }

    @Test
    void invalidYamlCannotReplaceAnActiveAllowlist() throws Exception {
        ConfigService service = new ConfigService(null);
        PluginSettings active = prepare("participants:\n  mode: ALLOWLIST\n");
        service.apply(active);
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(prepare("participants: [unterminated")));
        assertSame(active, service.settings());
        assertEquals(PluginSettings.ParticipantMode.ALLOWLIST, service.settings().participantMode());
    }

    @Test
    void rejectsUnknownModeAndInvalidDatePattern() {
        assertThrows(IllegalArgumentException.class, () -> prepare("participants:\n  mode: ALLLOWLIST\n"));
        assertThrows(IllegalArgumentException.class, () -> prepare("gui:\n  date-format: invalid-pattern\n"));
    }

    @Test
    void explicitNullValuesFallBackToDefaults() throws Exception {
        PluginSettings settings = prepare("participants:\n  mode: ~\ngui:\n  date-format: ~\n  title: ~\n");
        assertEquals(PluginSettings.ParticipantMode.EVERYONE, settings.participantMode());
        assertEquals("dd MMM yyyy, HH:mm", settings.dateFormat());
        assertTrue(settings.guiTitle().contains("%category%"));
    }

    @Test
    void invalidModeAndDatePatternNameTheirKeys() {
        IllegalArgumentException mode = assertThrows(IllegalArgumentException.class,
                () -> prepare("participants:\n  mode: '  '\n"));
        assertTrue(mode.getMessage().contains("participants.mode"));
        IllegalArgumentException date = assertThrows(IllegalArgumentException.class,
                () -> prepare("gui:\n  date-format: 'dd {'\n"));
        assertTrue(date.getMessage().contains("gui.date-format"));
    }

    @Test
    void bundledBlockMenuTitleMatchesTheDeprecatedConfigFallback() throws Exception {
        YamlConfiguration config = bundled("config.yml");
        YamlConfiguration messages = bundled("messages.yml");
        String title = messages.getString("gui.block-menu.title");
        assertNotNull(title);
        assertEquals(config.getString("gui.title"), title);
        assertEquals(title, prepare("{}\n").guiTitle());
    }

    private static YamlConfiguration bundled(String name) throws Exception {
        try (var stream = ConfigServiceTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(stream, name);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void aBadMessageDocumentPreservesBothPreviouslyLoadedDocuments() throws Exception {
        ConfigService config = new ConfigService(null);
        MessageService messages = new MessageService(null);
        PluginSettings active = prepare("participants:\n  mode: ALLOWLIST\n");
        config.apply(active);
        Path file = directory.resolve("messages.yml");
        Files.writeString(file, "tag: original\n");
        messages.apply(StrictYaml.load(file.toFile()));
        Files.writeString(file, "tag: [unterminated");

        assertThrows(IllegalArgumentException.class, () -> {
            PluginSettings nextConfig = prepare("participants:\n  mode: EVERYONE\n");
            var nextMessages = StrictYaml.load(file.toFile());
            config.apply(nextConfig);
            messages.apply(nextMessages);
        });
        assertSame(active, config.settings());
        assertEquals("original", messages.string("tag", Map.of(), null));
    }

    @Test
    void aValidPreparationDoesNotChangeActiveSettingsUntilApplied() throws Exception {
        ConfigService service = new ConfigService(null);
        PluginSettings original = prepare("participants:\n  mode: ALLOWLIST\n");
        service.apply(original);
        PluginSettings next = prepare("participants:\n  mode: EVERYONE\nitems:\n  enabled: true\n");
        assertSame(original, service.settings());
        service.apply(next);
        assertTrue(service.settings().itemsEnabled());
        assertEquals(PluginSettings.ParticipantMode.EVERYONE, service.settings().participantMode());
    }
}
