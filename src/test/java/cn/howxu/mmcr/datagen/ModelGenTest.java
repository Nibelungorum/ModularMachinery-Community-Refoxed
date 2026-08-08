package cn.howxu.mmcr.datagen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGenTest {

    @Test
    void generatedDynamicBlocksAreEmpty() {
        assertThat(ModelGen.generatedDynamicBlocks()).isEmpty();
    }
}
