package dev.remod.installer.install;

import java.util.List;

/**
 * Bundled-library set backed by real files on the test classpath
 * ({@code src/test/resources/remod-libs/}), so install tests exercise the same
 * extraction path the shipped jar uses.
 */
final class TestLibraries {

    private TestLibraries() {
    }

    static BundledLibraries create() {
        return BundledLibraries.of(List.of(
                new BundledLibraries.Library("dev.remod", "remod-common", "1.0.0",
                        "remod-common-test.jar"),
                new BundledLibraries.Library("dev.remod", "remod-api", "1.0.0",
                        "remod-api-test.jar"),
                new BundledLibraries.Library("dev.remod", "remod-loader", "1.0.0",
                        "remod-loader-test.jar")),
                "remod-api-dev-test.jar");
    }
}
