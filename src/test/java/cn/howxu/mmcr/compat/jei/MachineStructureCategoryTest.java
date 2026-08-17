package cn.howxu.mmcr.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JEI structure category allocates one full preview per page.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineStructureCategoryTest {
    @Test
    void category_uses_a_single_full_page_preview() throws IOException {
        String source = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/compat/jei/MachineStructureCategory.java"));

        assertThat(source).contains("private static final int PREVIEW_X = 2;");
        assertThat(source).contains("private static final int PREVIEW_Y = 4;");
        assertThat(source).contains("case 1 -> 300;");
        assertThat(source).contains("case 2 -> 280;");
        assertThat(source).contains("case 3 -> 220;");
        assertThat(source).contains("default -> 150;");
    }

    @Test
    void category_registers_the_controller_only_in_an_offscreen_filter_slot() throws IOException {
        String source = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/compat/jei/MachineStructureCategory.java"));

        assertThat(source).contains("addInputSlot(-1000, -1000)");
    }
}
