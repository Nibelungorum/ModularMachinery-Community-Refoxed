package cn.howxu.mmcr.compat.kubejs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class ExampleScriptCompatibilityTest {

    @Test
    void example_scripts_use_kubejs_26_java_loader_instead_of_legacy_java_global() throws Exception {
        for (String script : new String[]{"example/startup_scripts/machine.js", "example/server_scripts/machine.js"}) {
            String content = Files.readString(Path.of(script));

            assertThat(content).contains("Java.loadClass('java.util.LinkedHashMap')");
            assertThat(content).doesNotContain("new java.");
        }
    }

    @Test
    void example_scripts_avoid_modern_javascript_syntax_for_rhino() throws Exception {
        String content = Files.readString(Path.of("example/server_scripts/machine.js"));

        assertThat(content).doesNotContain("=>");
        assertThat(content).doesNotContain("`");
        assertThat(content).doesNotContain("const ");
        assertThat(content).doesNotContain("let ");
        assertThat(content).doesNotContain(".forEach(");
        assertThat(content).doesNotContain(".map(");
    }

    @Test
    void reactor_pattern_keeps_all_slices_the_same_height() throws Exception {
        String content = Files.readString(Path.of("example/server_scripts/machine.js"));

        assertThat(content).doesNotContain("['  AAAAA  ','         ','         ','         ','         ','         ','         ']\n");
        assertThat(content).contains(", ['  AAAAA  ','         ','         ','         ','         ','         ','         ','         ']\n");
    }

    @Test
    void server_pattern_iterates_each_slice_by_its_own_dimensions() throws Exception {
        String content = Files.readString(Path.of("example/server_scripts/machine.js"));

        assertThat(content).contains("var slice = slices[z]");
        assertThat(content).contains("var height = slice.length");
        assertThat(content).contains("var line = slice[row]");
        assertThat(content).contains("var width = line.length");
        assertThat(content).doesNotContain("slices[z][row][column]");
    }

    @Test
    void server_script_uses_existing_jsonio_parse_api_for_component_json() throws Exception {
        String content = Files.readString(Path.of("example/server_scripts/machine.js"));

        assertThat(content).doesNotContain("JsonIO.of");
        assertThat(content).contains("JsonIO.parseRaw(JSON.stringify(value))");
    }
}
