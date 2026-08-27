package dev.remod.common.net;

import dev.remod.common.io.IOUtil;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * The single place ReMod performs HTTP.
 *
 * <p>Three behaviours matter here and are all deliberate:</p>
 *
 * <ul>
 *   <li><b>Caching.</b> Every text fetch is cached on disk with its ETag. A
 *       repeated fetch inside the freshness window does no network I/O at all,
 *       and outside it sends {@code If-None-Match} so an unchanged manifest
 *       costs a 304 rather than a re-download. The version manifest is
 *       therefore fetched at most once per session.</li>
 *   <li><b>Offline mode.</b> When the network is unavailable, cached content is
 *       served regardless of age, and an operation with no cache fails with a
 *       message that says what is missing rather than a raw
 *       {@code UnknownHostException}.</li>
 *   <li><b>Verification.</b> Downloads that declare a SHA-1 are checked against
 *       it, and a mismatch deletes the file rather than leaving a corrupt jar
 *       on disk.</li>
 * </ul>
 *
 * <p>The client is created lazily and shared, so ReMod holds no connection
 * pool or background threads until something is actually downloaded.</p>
 */
public final class HttpFetcher {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Net");
    private static final String USER_AGENT = "ReMod/1.0 (+https://github.com/remod)";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final Path cacheDirectory;
    private volatile HttpClient client;
    private volatile boolean offline;

    public HttpFetcher(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    /** In offline mode nothing touches the network; only the cache is consulted. */
    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean isOffline() {
        return offline;
    }

    public Path cacheDirectory() {
        return cacheDirectory;
    }

    /** Created on first use so that a purely offline session opens no sockets. */
    private HttpClient client() {
        HttpClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(CONNECT_TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    /**
     * Fetches a text resource, using the on-disk cache when it is younger than
     * {@code maxAge} and revalidating with an ETag otherwise.
     *
     * @param cacheKey a stable, filesystem-safe name for this resource
     */
    public String fetchText(String url, String cacheKey, Duration maxAge) throws DownloadException {
        Path cacheFile = cacheDirectory.resolve(sanitize(cacheKey) + ".cache");
        Path etagFile = cacheDirectory.resolve(sanitize(cacheKey) + ".etag");

        String cached = readCache(cacheFile);
        if (cached != null && isFresh(cacheFile, maxAge)) {
            LOG.debug(() -> "Using cached " + cacheKey + " (still fresh)");
            return cached;
        }
        if (offline) {
            if (cached != null) {
                LOG.info("Offline: using cached " + cacheKey);
                return cached;
            }
            throw new DownloadException(
                    "Offline mode is on and " + cacheKey + " has never been downloaded",
                    "Turn off offline mode and retry once you have an internet connection.");
        }

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(toUri(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .GET();
            String etag = readCache(etagFile);
            if (etag != null && cached != null) {
                request.header("If-None-Match", etag.trim());
            }

            HttpResponse<String> response =
                    client().send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 304 && cached != null) {
                LOG.debug(() -> cacheKey + " unchanged (304), refreshing cache timestamp");
                touch(cacheFile);
                return cached;
            }
            if (response.statusCode() / 100 != 2) {
                if (cached != null) {
                    LOG.warn("Server returned HTTP " + response.statusCode() + " for " + cacheKey
                            + "; falling back to the cached copy");
                    return cached;
                }
                throw new DownloadException(
                        "Server returned HTTP " + response.statusCode() + " for " + url,
                        "The service may be temporarily unavailable; try again in a few minutes.");
            }

            String body = response.body();
            writeCache(cacheFile, body);
            response.headers().firstValue("ETag")
                    .ifPresent(value -> writeCache(etagFile, value));
            return body;
        } catch (DownloadException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (cached != null) {
                LOG.warn("Could not reach " + url + " (" + e + "); using the cached copy");
                return cached;
            }
            throw new DownloadException(
                    "Could not download " + url + ": " + e.getMessage(),
                    "Check your internet connection, proxy settings and firewall, then try again.", e);
        }
    }

    /**
     * Downloads a binary resource to {@code target}.
     *
     * <p>When {@code expectedSha1} is supplied and {@code target} already
     * matches it, the download is skipped entirely -- this is what makes
     * re-installing the same Minecraft version nearly free.</p>
     */
    public void downloadFile(String url, Path target, String expectedSha1,
                             ProgressListener progress) throws DownloadException {
        ProgressListener listener = progress == null ? ProgressListener.NONE : progress;
        try {
            if (Files.exists(target) && matchesSha1(target, expectedSha1)) {
                LOG.debug(() -> "Already have " + target.getFileName() + "; skipping download");
                listener.progress(target.getFileName().toString(), Files.size(target), Files.size(target));
                return;
            }
        } catch (IOException e) {
            // Fall through and re-download.
        }
        if (offline) {
            throw new DownloadException(
                    "Offline mode is on but " + target.getFileName() + " is not present",
                    "Turn off offline mode so ReMod can download the missing file.");
        }

        Path temp = null;
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temp = Files.createTempFile(parent, ".remod-dl-", ".part");

            HttpRequest request = HttpRequest.newBuilder(toUri(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new DownloadException(
                        "Server returned HTTP " + response.statusCode() + " for " + url,
                        "The file may have moved; try a different Minecraft version or retry later.");
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            String name = target.getFileName().toString();
            long done = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                long lastReport = 0;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    // Report at most every 256 KB to keep the GUI responsive
                    // without flooding the event queue.
                    if (done - lastReport >= 256 * 1024) {
                        lastReport = done;
                        listener.progress(name, done, total);
                    }
                }
            }
            listener.progress(name, done, total);

            if (expectedSha1 != null && !expectedSha1.isEmpty()) {
                String actual = IOUtil.sha1(temp);
                if (!actual.equalsIgnoreCase(expectedSha1)) {
                    throw new DownloadException(
                            "Checksum mismatch for " + name + " (expected " + expectedSha1
                                    + ", got " + actual + ")",
                            "The download was corrupted or intercepted. Retry; if it persists, "
                                    + "check for a proxy that rewrites downloads.");
                }
            }
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (DownloadException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DownloadException(
                    "Could not download " + url + ": " + e.getMessage(),
                    "Check your internet connection and try again.", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best effort cleanup of the partial download.
                }
            }
        }
    }

    private static boolean matchesSha1(Path file, String expectedSha1) throws IOException {
        if (expectedSha1 == null || expectedSha1.isEmpty()) {
            return false;
        }
        return IOUtil.sha1(file).equalsIgnoreCase(expectedSha1);
    }

    private static URI toUri(String url) throws DownloadException {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                throw new DownloadException("Refusing to fetch non-HTTP URL " + url,
                        "ReMod only downloads over http(s).");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new DownloadException("Malformed URL " + url, "Report this as a ReMod bug.", e);
        }
    }

    private boolean isFresh(Path cacheFile, Duration maxAge) {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return false;
        }
        try {
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
            return age >= 0 && age < maxAge.toMillis();
        } catch (IOException e) {
            return false;
        }
    }

    private String readCache(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.debug(() -> "Unreadable cache file " + file + ": " + e);
        }
        return null;
    }

    private void writeCache(Path file, String content) {
        try {
            Files.createDirectories(cacheDirectory);
            IOUtil.writeAtomically(file, content);
        } catch (IOException e) {
            LOG.debug(() -> "Unable to update cache file " + file + ": " + e);
        }
    }

    private void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
                    System.currentTimeMillis()));
        } catch (IOException e) {
            // Not fatal: the cache simply revalidates again next time.
        }
    }

    /** Turns an arbitrary cache key into a safe file name. */
    static String sanitize(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (char c : key.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '.' ? c : '_');
        }
        return sb.toString();
    }
}
