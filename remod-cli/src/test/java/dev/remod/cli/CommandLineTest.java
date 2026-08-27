package dev.remod.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineTest {

    @Test
    void separatesTheVerbFromPositionalArguments() {
        CommandLine line = CommandLine.parse(new String[]{"create", "MyMod", "extra"});

        assertEquals("create", line.verb());
        assertEquals(List.of("MyMod", "extra"), line.positional());
        assertEquals("MyMod", line.positional(0));
        assertNull(line.positional(5));
    }

    @Test
    void readsOptionsInBothSpellings() {
        CommandLine line = CommandLine.parse(new String[]{
                "install", "1.21.4", "--directory", "/games/mc", "--minecraft=1.20.1"});

        assertEquals("/games/mc", line.option("directory", null));
        assertEquals("1.20.1", line.option("minecraft", null));
        assertEquals("fallback", line.option("absent", "fallback"));
        assertTrue(line.has("directory"));
        assertFalse(line.has("absent"));
    }

    @Test
    void treatsABareOptionAsAFlag() {
        CommandLine line = CommandLine.parse(new String[]{"test", "--verbose", "--mods", "libs"});

        assertTrue(line.flag("verbose"));
        assertFalse(line.flag("mods"));
        assertEquals("libs", line.option("mods", null));
    }

    @Test
    void handlesAFlagAtTheEndOfTheLine() {
        CommandLine line = CommandLine.parse(new String[]{"install", "1.21.4", "--no-profile"});

        assertEquals("1.21.4", line.positional(0));
        assertTrue(line.flag("no-profile"));
    }

    @Test
    void handlesAnEmptyCommandLine() {
        CommandLine line = CommandLine.parse(new String[0]);

        assertNull(line.verb());
        assertTrue(line.positional().isEmpty());
        assertTrue(line.options().isEmpty());
    }

    @Test
    void toleratesANullArgumentArray() {
        assertNull(CommandLine.parse(null).verb());
    }
}
