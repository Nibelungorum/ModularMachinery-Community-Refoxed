package cn.howxu.mmcr.client.model;

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

/** Fork with and without the actual Athena artifact; ModList=false in a shared JVM is not an absence test.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class WorldFaceClasspathTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void world_models_work_with_the_selected_runtime_classpath(boolean installed) throws Exception {
        var entries = new LinkedHashSet<String>();
        var loader = getClass().getClassLoader();
        for (var cl = loader; cl != null; cl = cl.getParent()) {
            if (cl instanceof URLClassLoader urls)
                for (var url : urls.getURLs()) entries.add(Path.of(url.toURI()).toString());
        }
        entries.addAll(List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        var athena = Class.forName("earth.terrarium.athena.api.client.neoforge.AthenaBakedModel", false, loader)
                .getProtectionDomain().getCodeSource().getLocation();
        if (!installed) assertThat(entries.remove(Path.of(athena.toURI()).toString())).isTrue();
        var log = temp.resolve("probe.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", String.join(File.pathSeparator, entries), WorldFaceClasspathProbe.class.getName(),
                Boolean.toString(installed)).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(90, TimeUnit.SECONDS)).as("world-face probe timed out").isTrue();
            assertThat(process.exitValue()).withFailMessage("%s", Files.readString(log)).isZero();
        } finally {
            process.destroyForcibly();
        }
    }
}
