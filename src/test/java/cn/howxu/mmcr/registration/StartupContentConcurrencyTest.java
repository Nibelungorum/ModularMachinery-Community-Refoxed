package cn.howxu.mmcr.registration;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs each initialization order in a fresh JVM, before any shared TestBootstrap initializes the registries.
 *
 * @author howxu <dev@howxu.cn>
 */
class StartupContentConcurrencyTest {
    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(strings = {"items-first", "scripts-first", "concurrent"})
    void startup_registration_is_serialized_without_class_initialization_deadlocks(String scenario) throws Exception {
        Path output = temporaryDirectory.resolve(scenario + ".log");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", testClasspath(), StartupContentConcurrencyProbe.class.getName(), scenario)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                Process dump = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "jcmd").toString(),
                        Long.toString(process.pid()), "Thread.print")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(output.toFile()))
                        .start();
                try {
                    dump.waitFor(5, TimeUnit.SECONDS);
                } finally {
                    dump.destroyForcibly();
                }
            }
            assertThat(finished)
                    .withFailMessage("Startup probe timed out (%s):%n%s", scenario, Files.readString(output))
                    .isTrue();
            assertThat(process.exitValue())
                    .withFailMessage("Startup probe failed (%s):%n%s", scenario, Files.readString(output))
                    .isZero();
        } finally {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static String testClasspath() throws Exception {
        // Gradle's test worker classpath itself only contains its launcher jar.
        var entries = new LinkedHashSet<String>();
        for (ClassLoader loader = StartupContentConcurrencyTest.class.getClassLoader(); loader != null;
             loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) entries.add(Path.of(url.toURI()).toString());
            }
        }
        entries.addAll(List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        return String.join(File.pathSeparator, entries);
    }
}
