package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingComponentTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void processingComponentConvertsLegacySingleTagToTagList() {
        MachineComponent component = new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        BlockEntity container = null;
        BlockPos pos = new BlockPos(1, 2, 3);
        ProcessingComponent processing = new ProcessingComponent(component, container, pos, pos, "input_a");

        assertThat(processing.tags()).containsExactly("input_a");
        assertThat(processing.tag()).isEqualTo("input_a");
    }

    @Test
    void processingComponentDefaultsNullTagToEmptyList() {
        MachineComponent component = new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        BlockEntity container = null;
        BlockPos pos = new BlockPos(0, 0, 0);
        ProcessingComponent processing = new ProcessingComponent(component, container, pos, pos, (String) null);

        assertThat(processing.tags()).isEmpty();
        assertThat(processing.tag()).isNull();
    }

    @Test
    void processingComponentAcceptsMultiTagList() {
        MachineComponent component = new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        BlockPos pos = new BlockPos(0, 0, 0);
        ProcessingComponent processing = new ProcessingComponent(component, null, pos, pos, List.of("input_a", "fast"));

        assertThat(processing.tags()).containsExactly("input_a", "fast");
        assertThat(processing.tag()).isEqualTo("input_a");
    }
}
