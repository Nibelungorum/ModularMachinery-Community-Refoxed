package cn.howxu.mmcr.publicapi;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the built binary with an explicitly isolated compiler and a separate main-runtime smoke test.
 * @author howxu <dev@howxu.cn>
 */
class PublicApiArtifactTest {
    @TempDir Path temporary;

    static Path apiJar() {
        return Path.of(requiredProperty("mmcr.apiJar"));
    }

    static String requiredProperty(String name) {
        String value = System.getProperty(name);
        assertNotNull(value, "Gradle must supply " + name);
        assertFalse(value.isBlank(), name);
        return value;
    }

    static List<String> classNames() throws Exception {
        try (JarFile jar = new JarFile(apiJar().toFile())) {
            return jar.stream().filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                    .map(entry -> entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.'))
                    .filter(name -> !name.endsWith("package-info")).sorted().toList();
        }
    }

    static URLClassLoader isolatedLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(apiJar().toUri().toURL());
        for (String entry : requiredProperty("mmcr.apiExternalClasspath").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    @Test
    void artifact_contains_only_public_class_files() throws Exception {
        assertTrue(Files.isRegularFile(apiJar()), "apiJar must actually be built");
        try (JarFile jar = new JarFile(apiJar().toFile())) {
            List<String> entries = jar.stream().filter(entry -> !entry.isDirectory())
                    .map(entry -> entry.getName()).toList();
            assertTrue(entries.contains("cn/howxu/mmcr/publicapi/Machines.class"));
            assertTrue(entries.contains("cn/howxu/mmcr/publicapi/recipe/requirement/RequirementExtension.class"));
            for (String entry : entries) {
                if (entry.equals("META-INF/MANIFEST.MF")) continue;
                assertTrue(entry.startsWith("cn/howxu/mmcr/publicapi/") && entry.endsWith(".class"), entry);
            }
        }
    }

    @Test
    void clean_classpath_compiles_public_consumption_but_rejects_core_and_internal() throws Exception {
        String publicUse = """
                package example.addon;
                import cn.howxu.mmcr.publicapi.Machines;
                import cn.howxu.mmcr.publicapi.machine.MachineDraft;
                import cn.howxu.mmcr.publicapi.machine.MachineSpec;
                import net.minecraft.resources.ResourceLocation;
                public class Positive {
                    public MachineSpec declare(ResourceLocation id) {
                        MachineDraft draft = Machines.machine(id);
                        return draft.recipePool(id).build();
                    }
                }
                """;
        assertTrue(compile("Positive", publicUse, true));
        for (String forbidden : List.of("cn.howxu.mmcr.api.machine.definition.MachineBuilder",
                "cn.howxu.mmcr.internal.api.facade.machine.MachineAdapters",
                "cn.howxu.mmcr.MMCR")) {
            String source = "package example.addon; public class Negative { public " + forbidden + " value; }";
            assertFalse(compile("Negative", source, false), forbidden + " must be unavailable");
        }
        try (URLClassLoader loader = isolatedLoader()) {
            assertNotNull(Class.forName("cn.howxu.mmcr.publicapi.machine.MachineDraft", false, loader));
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("cn.howxu.mmcr.internal.api.facade.machine.MachineAdapters", false, loader));
        }
    }

    private boolean compile(String name, String source, boolean positive) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests require a JDK, not a JRE");
        Path work = Files.createTempDirectory(temporary, name);
        Path sourceFile = work.resolve(name + ".java");
        Files.writeString(sourceFile, source);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            String classpath = apiJar() + File.pathSeparator + requiredProperty("mmcr.apiExternalClasspath");
            boolean success = compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "-implicit:none", "-sourcepath", work.toString(),
                            "-classpath", classpath, "-d", work.toString()), null,
                    manager.getJavaFileObjects(sourceFile)).call();
            if (positive) assertTrue(success, diagnostics.getDiagnostics().toString());
            else assertTrue(diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
                    && (d.getCode().equals("compiler.err.doesnt.exist")
                    || d.getCode().startsWith("compiler.err.cant.resolve"))), diagnostics.getDiagnostics().toString());
            return success;
        }
    }

    @Test
    void external_compiled_fixture_runs_with_the_main_implementation() throws Exception {
        Class.forName("cn.howxu.mmcr.test.TestBootstrap").getMethod("bootstrap").invoke(null);
        URL output = Path.of(requiredProperty("mmcr.apiUsageClasses")).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {output}, getClass().getClassLoader())) {
            Class<?> fixture = Class.forName("example.addon.TypedFixture", true, loader);
            assertEquals("payload", fixture.getMethod("dataRoundTrip").invoke(null));
            long amount = (1L << 40) + 7;
            assertEquals(amount, fixture.getMethod("codecRoundTrip", long.class).invoke(null, amount));
            assertEquals(List.of("machine.example_addon.first", "machine.example_addon.second"),
                    fixture.getMethod("declarationRoundTrip").invoke(null));
        }
    }
}
