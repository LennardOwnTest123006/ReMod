package dev.remod.transform.mapping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Mojang's ProGuard mapping format, including the direction trap: the
 * readable name is on the left and the name actually in the jar is on the
 * right, which is the opposite of what "deobfuscation mapping" suggests.
 */
class ProGuardMappingParserTest {

    /** Shaped exactly like Mojang's published client mappings. */
    private static final String SAMPLE = String.join("\n",
            "# (c) 2024 Mojang AB",
            "net.minecraft.commands.Commands -> fx:",
            "    com.mojang.brigadier.CommandDispatcher dispatcher -> b",
            "    int LEVEL_OWNERS -> d",
            "    1:12:void performCommand(java.lang.String) -> a",
            "    45:67:boolean sendCommands(net.minecraft.server.level.ServerPlayer) -> c",
            "net.minecraft.world.entity.player.Abilities -> cwx:",
            "    boolean mayfly -> c",
            "    boolean flying -> b",
            "    float flyingSpeed -> f",
            "    void setFlyingSpeed(float) -> a");

    @Test
    void mapsClassesFromReadableNameToTheNameInTheJar() {
        MappingSet mappings = ProGuardMappingParser.parse(SAMPLE);

        assertEquals(2, mappings.classCount());
        assertEquals("fx", mappings.runtimeClassName("net.minecraft.commands.Commands"));
        assertEquals("cwx",
                mappings.runtimeClassName("net.minecraft.world.entity.player.Abilities"));
    }

    @Test
    void mapsFieldsAndMethods() {
        MappingSet mappings = ProGuardMappingParser.parse(SAMPLE);

        assertEquals("b", mappings.runtimeFieldName(
                "net.minecraft.commands.Commands", "dispatcher"));
        assertEquals("c", mappings.runtimeFieldName(
                "net.minecraft.world.entity.player.Abilities", "mayfly"));
        assertEquals("f", mappings.runtimeFieldName(
                "net.minecraft.world.entity.player.Abilities", "flyingSpeed"));
        assertEquals("a", mappings.runtimeMethodName(
                "net.minecraft.commands.Commands", "performCommand"));
        assertEquals("a", mappings.runtimeMethodName(
                "net.minecraft.world.entity.player.Abilities", "setFlyingSpeed"));
    }

    @Test
    void stripsTheLineNumberPrefixFromMethodLines() {
        MappingSet mappings = ProGuardMappingParser.parse(SAMPLE);

        // "45:67:boolean sendCommands(...) -> c" must not keep the 45:67:.
        assertEquals("c", mappings.runtimeMethodName(
                "net.minecraft.commands.Commands", "sendCommands"));
    }

    @Test
    void looksUpByTheObfuscatedNameToo() {
        MappingSet mappings = ProGuardMappingParser.parse(SAMPLE);

        MappingSet.ClassMapping commands = mappings.classByObfuscatedName("fx").orElseThrow();
        assertEquals("net.minecraft.commands.Commands", commands.deobfuscatedName());
    }

    @Test
    void fallsBackToTheReadableNameForAnythingUnmapped() {
        MappingSet mappings = ProGuardMappingParser.parse(SAMPLE);

        // In a deobfuscated development environment the readable name IS the
        // runtime name, so falling back is correct rather than a failure.
        assertEquals("net.minecraft.nowhere.Absent",
                mappings.runtimeClassName("net.minecraft.nowhere.Absent"));
        assertEquals("absentField", mappings.runtimeFieldName(
                "net.minecraft.commands.Commands", "absentField"));
        assertEquals("absentField", mappings.runtimeFieldName(
                "net.minecraft.nowhere.Absent", "absentField"));
    }

    @Test
    void skipsUnreadableLinesRatherThanLosingTheWholeFile() {
        String withJunk = String.join("\n",
                "this line has no arrow at all",
                "net.minecraft.commands.Commands -> fx:",
                "    garbage with no arrow",
                "    com.mojang.brigadier.CommandDispatcher dispatcher -> b",
                "    -> orphanedArrow",
                "    another bad one ->");

        MappingSet mappings = ProGuardMappingParser.parse(withJunk);

        assertEquals(1, mappings.classCount());
        assertEquals("b", mappings.runtimeFieldName(
                "net.minecraft.commands.Commands", "dispatcher"));
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        MappingSet mappings = ProGuardMappingParser.parse(String.join("\n",
                "# a comment",
                "",
                "net.minecraft.Thing -> a:",
                "",
                "    int value -> b"));

        assertEquals(1, mappings.classCount());
        assertEquals("b", mappings.runtimeFieldName("net.minecraft.Thing", "value"));
    }

    @Test
    void ignoresMembersThatAppearBeforeAnyClass() {
        MappingSet mappings = ProGuardMappingParser.parse(String.join("\n",
                "    int orphan -> a",
                "net.minecraft.Thing -> b:",
                "    int value -> c"));

        assertEquals(1, mappings.classCount());
        assertEquals("c", mappings.runtimeFieldName("net.minecraft.Thing", "value"));
    }

    @Test
    void anEmptyMappingSetMapsNothingAndSaysSo() {
        MappingSet empty = MappingSet.empty();

        assertTrue(empty.isEmpty());
        assertEquals(0, empty.classCount());
        assertEquals("net.minecraft.commands.Commands",
                empty.runtimeClassName("net.minecraft.commands.Commands"));
        assertFalse(empty.classByName("anything").isPresent());
    }

    @Test
    void parsesAFileWithNoTrailingNewline() {
        MappingSet mappings = ProGuardMappingParser.parse(
                "net.minecraft.Thing -> a:\n    int value -> b");

        assertEquals(1, mappings.classCount());
        assertEquals("b", mappings.runtimeFieldName("net.minecraft.Thing", "value"));
    }
}
