package dev.everyblock.message;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageServiceTest {
    @Test
    void configuredLinesAcceptsYamlLists() {
        assertEquals(List.of("line one", "", "line three"),
                MessageService.configuredLines(List.of("line one", "", "line three")));
    }

    @Test
    void configuredLinesKeepsLegacyScalarValuesWorking() {
        assertEquals(List.of("one old line"), MessageService.configuredLines("one old line"));
        assertEquals(List.of(), MessageService.configuredLines(""));
    }
}
