package dev.everyblock.gui;

public enum BlockFilter {
    ALL,
    MISSING,
    FOUND;

    public BlockFilter next() {
        return switch (this) {
            case ALL -> FOUND;
            case FOUND -> MISSING;
            case MISSING -> ALL;
        };
    }
}
