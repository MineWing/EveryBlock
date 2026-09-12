package dev.everyblock.config;

import dev.everyblock.message.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
