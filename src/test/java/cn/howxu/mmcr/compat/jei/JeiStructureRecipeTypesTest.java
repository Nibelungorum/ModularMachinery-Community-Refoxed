package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.datagen.Translations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the shared JEI multiblock structure category identity.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiStructureRecipeTypesTest {

    @Test
    void structureTypeUsesOneStableCommonCategoryId() {
        assertThat(JeiMachineRecipeTypes.STRUCTURE.getUid())
                .isEqualTo(MMCR.id("multiblock_structure"));
        assertThat(JeiMachineRecipeTypes.STRUCTURE.getRecipeClass())
                .isEqualTo(MachineStructureDisplay.class);
    }

    @Test
    void structuresUseOnlyTheSharedCategoryType() throws Exception {
        String types = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/howxu/mmcr/compat/jei/JeiMachineRecipeTypes.java"));
        String plugin = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/howxu/mmcr/compat/jei/JeiPlugin.java"));

        assertThat(types).doesNotContain("structureFor(");
        assertThat(plugin).doesNotContain("structureFor(");
        assertThat(plugin).doesNotContain("addRecipeCatalyst(controller, JeiMachineRecipeTypes.STRUCTURE)");
    }

    @Test
    void translationsNameTheStructureCategoryInBothSupportedLanguages() {
        assertThat(Translations.ALL.get("en_us"))
                .containsEntry("jei.mmcr.multiblock_structure", "Multiblock Structures");
        assertThat(Translations.ALL.get("zh_cn"))
                .containsEntry("jei.mmcr.multiblock_structure", "多方块结构");
    }
}
