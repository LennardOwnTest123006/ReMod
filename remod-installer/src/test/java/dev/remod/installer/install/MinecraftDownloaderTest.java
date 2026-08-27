package dev.remod.installer.install;

import com.sun.net.httpserver.HttpServer;
import dev.remod.common.io.IOUtil;
import dev.remod.common.log.ReModLog;
import dev.remod.common.net.DownloadException;
import dev.remod.common.net.HttpFetcher;
import dev.remod.installer.manifest.MinecraftVersionEntry;
import dev.remod.loader.ReModPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Minecraft download path against a real HTTP server.
 *
 * <p>Mojang's endpoints are not reachable from a build machine, and depending
 * on them would make the suite flaky anyway. Serving the same documents locally
 * covers what actually matters: that the manifest entry's URL is followed, the
 * version JSON is written where the launcher looks for it, the client jar is
 * fetched and checksum-verified, and a corrupted download is rejected rather
 * than left on disk.</p>
 */
class MinecraftDownloaderTest {

    @TempDir
    Path minecraft;

    private HttpServer server;
    private String baseUrl;
    private final Map<String, byte[]> routes = new HashMap<>();
    private final Map<String, Integer> hits = new HashMap<>();
    private ReModPaths paths;
    private MinecraftDownloader downloader;

    /** Stands in for the real client jar; its contents are irrelevant, its SHA-1 is not. */
    private static final byte[] CLIENT_JAR =
            "PK pretend this is minecraft".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void startServer(@TempDir Path cache) throws IOException {
        ReModLog.reset();
        paths = new ReModPaths(minecraft);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            hits.merge(path, 1, Integer::sum);
            byte[] body = routes.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        downloader = new MinecraftDownloader(new HttpFetcher(cache));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        ReModLog.reset();
    }

    private void serve(String path, byte[] body) {
        routes.put(path, body);
    }

    private static String sha1(byte[] bytes) throws Exception {
        return IOUtil.toHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }

    /** A version JSON shaped like Mojang's, pointing at our stand-in client jar. */
    private String versionJson(String clientSha1) {
        return "{\"id\":\"1.21.4\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"downloads\":{\"client\":{"
                + "\"url\":\"" + baseUrl + "/client.jar\","
                + "\"sha1\":\"" + clientSha1 + "\","
                + "\"size\":" + CLIENT_JAR.length + "}}}";
    }

    private MinecraftVersionEntry entry() {
        return new MinecraftVersionEntry("1.21.4", MinecraftVersionEntry.Type.RELEASE,
                baseUrl + "/1.21.4.json", null, Instant.now());
    }

    @Test
    void downloadsTheVersionJsonAndClientJarWhereTheLauncherLooks() throws Exception {
        serve("/1.21.4.json", versionJson(sha1(CLIENT_JAR)).getBytes(StandardCharsets.UTF_8));
        serve("/client.jar", CLIENT_JAR);

        MinecraftDownloader.Result result = downloader.download(paths, entry(), null);

        Path directory = paths.versionsDirectory().resolve("1.21.4");
        assertTrue(Files.isRegularFile(directory.resolve("1.21.4.json")));
        assertTrue(Files.isRegularFile(directory.resolve("1.21.4.jar")));
        assertArrayEquals(CLIENT_JAR, Files.readAllBytes(directory.resolve("1.21.4.jar")));
        assertTrue(result.versionJsonWritten());
        assertTrue(result.clientJarWritten());
        assertFalse(result.wasAlreadyPresent());
        assertTrue(MinecraftDownloader.isDownloaded(paths, "1.21.4"));
    }

    @Test
    void writesTheVersionJsonVerbatimSoItsChecksumStillMatches() throws Exception {
        String json = versionJson(sha1(CLIENT_JAR));
        serve("/1.21.4.json", json.getBytes(StandardCharsets.UTF_8));
        serve("/client.jar", CLIENT_JAR);

        downloader.download(paths, entry(), null);

        // Reformatting would break the launcher's own verification of this file.
        assertEquals(json, Files.readString(
                paths.versionsDirectory().resolve("1.21.4/1.21.4.json"), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAClientJarWhoseChecksumDoesNotMatch() throws Exception {
        serve("/1.21.4.json",
                versionJson("0000000000000000000000000000000000000000")
                        .getBytes(StandardCharsets.UTF_8));
        serve("/client.jar", CLIENT_JAR);

        DownloadException error = assertThrows(DownloadException.class,
                () -> downloader.download(paths, entry(), null));

        assertTrue(error.getMessage().contains("Checksum mismatch"), error.getMessage());
        // A corrupt jar must not be left where the launcher would run it.
        assertFalse(Files.exists(paths.versionsDirectory().resolve("1.21.4/1.21.4.jar")));
    }

    @Test
    void skipsAClientJarThatIsAlreadyPresentAndCorrect() throws Exception {
        serve("/1.21.4.json", versionJson(sha1(CLIENT_JAR)).getBytes(StandardCharsets.UTF_8));
        serve("/client.jar", CLIENT_JAR);

        downloader.download(paths, entry(), null);
        int firstJarHits = hits.getOrDefault("/client.jar", 0);
        MinecraftDownloader.Result second = downloader.download(paths, entry(), null);

        assertEquals(firstJarHits, hits.getOrDefault("/client.jar", 0),
                "a matching jar should not be downloaded twice");
        assertTrue(second.wasAlreadyPresent());
        assertEquals("already downloaded", second.summary());
    }

    @Test
    void reportsAVersionWithNoClientDownload() {
        serve("/1.21.4.json",
                "{\"id\":\"1.21.4\",\"downloads\":{}}".getBytes(StandardCharsets.UTF_8));

        DownloadException error = assertThrows(DownloadException.class,
                () -> downloader.download(paths, entry(), null));
        assertTrue(error.getMessage().contains("no client download"), error.getMessage());
    }

    @Test
    void reportsAnUnreachableUrlWithAUsefulSuggestion() {
        MinecraftVersionEntry missing = new MinecraftVersionEntry("1.21.4",
                MinecraftVersionEntry.Type.RELEASE, baseUrl + "/absent.json", null,
                Instant.now());

        DownloadException error = assertThrows(DownloadException.class,
                () -> downloader.download(paths, missing, null));
        assertTrue(error.suggestion() != null && !error.suggestion().isBlank());
    }

    @Test
    void reportsAVersionEntryWithNoUrl() {
        MinecraftVersionEntry noUrl = new MinecraftVersionEntry("1.21.4",
                MinecraftVersionEntry.Type.RELEASE, null, null, Instant.now());

        DownloadException error = assertThrows(DownloadException.class,
                () -> downloader.download(paths, noUrl, null));
        assertTrue(error.getMessage().contains("no version JSON URL"), error.getMessage());
    }

    @Test
    void reportsProgressForTheGui() throws Exception {
        serve("/1.21.4.json", versionJson(sha1(CLIENT_JAR)).getBytes(StandardCharsets.UTF_8));
        serve("/client.jar", CLIENT_JAR);
        List<String> steps = new ArrayList<>();

        downloader.download(paths, entry(), (what, done, total) -> steps.add(what));

        assertTrue(steps.stream().anyMatch(s -> s.contains("version file")), steps.toString());
        assertTrue(steps.stream().anyMatch(s -> s.contains("client")), steps.toString());
    }
}
