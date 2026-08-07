package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveMachineRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void setParallelismClampsToActiveMaximumAndPersistsClampedValue() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("active_parallel_clamp"),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of());
        RecipeRegistry.register(recipe);

        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);
        active.setParallelism(64);
        assertThat(active.getParallelism()).isEqualTo(16);
        active.setMaxParallelism(4);
        assertThat(active.getParallelism()).isEqualTo(4);
        active.setMaxParallelism(16);
        active.setParallelism(0);
        assertThat(active.getParallelism()).isEqualTo(1);

        active.setParallelism(64);
        ActiveMachineRecipe fromNbt = new ActiveMachineRecipe(active.serialize());
        assertThat(fromNbt.getMaxParallelism()).isEqualTo(16);
        assertThat(fromNbt.getParallelism()).isEqualTo(16);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        active.serialize(output);
        CompoundTag tag = output.buildResult();
        ActiveMachineRecipe fromValueInput = ActiveMachineRecipe.from(TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()), tag));

        assertThat(fromValueInput.getMaxParallelism()).isEqualTo(16);
        assertThat(fromValueInput.getParallelism()).isEqualTo(16);
    }
}
