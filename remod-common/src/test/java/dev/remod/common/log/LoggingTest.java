package dev.remod.common.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {

    @Test
    void routesRecordsToEverySinkWithChannelAndLevel() {
        ReModLog.reset();
        MemoryLogSink sink = new MemoryLogSink();
        ReModLog.addSink(sink);
        try {
            ReModLog.core().info("Starting ReMod");
            ReModLog.get("simplemod").warn("Config missing, using defaults");

            List<String> lines = sink.lines();
            assertTrue(lines.get(lines.size() - 2).contains("[ReMod/INFO] Starting ReMod"),
                    lines.toString());
            assertTrue(lines.get(lines.size() - 1)
                    .contains("[simplemod/WARN] Config missing, using defaults"), lines.toString());
        } finally {
            ReModLog.reset();
        }
    }

    @Test
    void respectsTheLevelThresholdAndSkipsSupplierWork() {
        ReModLog.reset();
        MemoryLogSink sink = new MemoryLogSink();
        ReModLog.addSink(sink);
        ReModLog.setLevel(LogLevel.WARN);
        boolean[] evaluated = {false};
        try {
            ReModLogger log = ReModLog.get("test");
            log.debug(() -> {
                evaluated[0] = true;
                return "expensive";
            });
            log.info("also filtered");
            log.error("kept");

            assertFalse(evaluated[0], "a filtered supplier must never be invoked");
            assertEquals(1, sink.records().size());
            assertEquals(LogLevel.ERROR, sink.records().get(0).level());
        } finally {
            ReModLog.reset();
        }
    }

    @Test
    void aFailingSinkDoesNotSilenceTheOthers() {
        ReModLog.reset();
        MemoryLogSink good = new MemoryLogSink();
        ReModLog.addSink(record -> {
            throw new IllegalStateException("sink is broken");
        });
        ReModLog.addSink(good);
        try {
            ReModLog.core().info("still delivered");
            assertEquals(1, good.records().size());
        } finally {
            ReModLog.reset();
        }
    }

    @Test
    void writesToDiskAndRotatesThePreviousRun(@TempDir Path dir) throws Exception {
        ReModLog.reset();
        Path logFile = dir.resolve("logs/remod.log");
        try {
            FileLogSink first = new FileLogSink(logFile);
            ReModLog.addSink(first);
            ReModLog.core().error("run one");
            first.close();

            FileLogSink second = new FileLogSink(logFile);
            ReModLog.addSink(second);
            ReModLog.core().error("run two");
            second.close();

            assertTrue(Files.readString(logFile).contains("run two"));
            assertFalse(Files.readString(logFile).contains("run one"));
            try (var stream = Files.list(dir.resolve("logs"))) {
                // The previous run was archived rather than discarded.
                assertEquals(2, stream.count());
            }
        } finally {
            ReModLog.reset();
        }
    }

    @Test
    void formatsErrorsWithTheirStackTrace() {
        LogRecord record = new LogRecord(java.time.Instant.now(), LogLevel.ERROR, "ReMod",
                "boom", new IllegalStateException("cause"), "main");
        assertFalse(LogFormat.line(record).contains("IllegalStateException"));
        assertTrue(LogFormat.full(record).contains("IllegalStateException: cause"));
    }
}
