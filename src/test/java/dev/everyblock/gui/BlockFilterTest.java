package dev.everyblock.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockFilterTest {
    @Test
    void cyclesAllFoundMissing() {
        assertEquals(BlockFilter.FOUND, BlockFilter.ALL.next());
        assertEquals(BlockFilter.MISSING, BlockFilter.FOUND.next());
        assertEquals(BlockFilter.ALL, BlockFilter.MISSING.next());
    }
}
