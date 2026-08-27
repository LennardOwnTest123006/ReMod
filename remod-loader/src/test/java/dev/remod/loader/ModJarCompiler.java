package dev.remod.loader;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Compiles mod source and packages it into a real jar.
 *
 * <p>Used by the integration tests so they exercise the genuine path -- javac
 * output, a zip archive, a fresh class loader -- rather than hand-built stubs
 * that would not catch a class-loading or manifest problem.</p>
 */
public final class ModJarCompiler {

    private ModJarCompiler() {
    }

    /**
     * Compiles sources and writes them into a mod jar alongside a manifest.
     *
     * @param sources fully qualified class name to Java source
     */
    public static Path buildModJar(Path modsDirectory, String fileName, String manifestJson,
                                   Map<String, String> sources) throws IOException {
        Path work = Files.createTempDirectory("remod-modjar");
        Path classes = work.resolve("classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("These tests need a JDK (javac), not a JRE");
        }
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((className, source) -> units.add(new InMemorySource(className, source)));

        List<String> options = List.of(
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString());
        StringWriterCollector diagnostics = new StringWriterCollector();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean ok = compiler.getTask(diagnostics.writer(), fileManager, null, options,
                    null, units).call();
            if (!ok) {
                throw new IllegalStateException("Test mod failed to compile:\n"
                        + diagnostics.text());
            }
        }

        Files.createDirectories(modsDirectory);
        Path jar = modsDirectory.resolve(fileName);
        try (OutputStream out = Files.newOutputStream(jar);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("remod.mod.json"));
            zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            try (java.util.stream.Stream<Path> stream = Files.walk(classes)) {
                List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
                for (Path file : files) {
                    zip.putNextEntry(new ZipEntry(classes.relativize(file).toString()
                            .replace('\\', '/')));
                    zip.write(Files.readAllBytes(file));
                    zip.closeEntry();
                }
            }
        }
        return jar;
    }

    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String source;

        InMemorySource(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    /** Collects compiler output so a failure message shows what went wrong. */
    private static final class StringWriterCollector {

        private final java.io.StringWriter writer = new java.io.StringWriter();

        java.io.Writer writer() {
            return writer;
        }

        String text() {
            return writer.toString();
        }
    }
}
