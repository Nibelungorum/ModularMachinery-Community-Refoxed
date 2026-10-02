package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.SourceOutput;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Rarity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeOutputCodecTest {

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void roundTripsItemsAndFluids() {
        MachineOutput item = new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 1F);
        MachineOutput fluid = new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 250), 0.8F);
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(item, 4L),
                new MachineOutputAmount(fluid, 250L)));

        List<MachineOutputAmount> decoded = RecipeOutputCodec.read(data);

        assertThat(decoded).hasSize(2);
        assertThat(decoded.get(0).output()).isInstanceOf(MachineOutput.ItemOutput.class);
        assertThat(((MachineOutput.ItemOutput) decoded.get(0).output()).stack().getCount()).isEqualTo(1);
        assertThat(decoded.get(0).amount()).isEqualTo(4L);
        assertThat(decoded.get(1).output()).isInstanceOf(MachineOutput.FluidOutput.class);
        assertThat(((MachineOutput.FluidOutput) decoded.get(1).output()).stack().getAmount()).isEqualTo(1);
        assertThat(decoded.get(1).amount()).isEqualTo(250L);
        assertThat(decoded.get(1).output().chance()).isEqualTo(0.8F);
    }

    @Test
    void emptyListWritesNothingAndReadsEmpty() {
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of());
        assertThat(data.contains(RecipeOutputCodec.OUTPUT_KEY)).isFalse();
        assertThat(RecipeOutputCodec.read(data)).isEmpty();
    }

    @Test
    void materializesDeclaredRarityForTheTransportedItem() {
        ItemStack stack = new ItemStack(Items.STONE);
        var components = new DataComponentPredicateSet(Map.of(DataComponents.RARITY,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive("rare")))));
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(
                new MachineOutput.ItemOutput(stack, 1F, components), 1L)));

        var decoded = (MachineOutput.ItemOutput) RecipeOutputCodec.read(data).getFirst().output();

        assertThat(decoded.stack().getRarity()).isEqualTo(Rarity.RARE);
        assertThat(stack.getRarity()).isEqualTo(Rarity.COMMON);
    }

    @Test
    void preservesLongFluidAmountsWithAStackTemplate() {
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(
                new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 1), 1F), Long.MAX_VALUE)));

        List<MachineOutputAmount> decoded = RecipeOutputCodec.read(data);

        assertThat(decoded).singleElement().satisfies(output -> {
            assertThat(output.amount()).isEqualTo(Long.MAX_VALUE);
            assertThat(((MachineOutput.FluidOutput) output.output()).stack().getAmount()).isEqualTo(1);
        });
    }

    @Test
    void missingKeyReadsAsEmpty() {
        assertThat(RecipeOutputCodec.read(new CompoundTag())).isEmpty();
    }

    @Test
    void roundTripsSourceTemplateAndParallelLongTotalThroughTheGenericOutputCodec() {
        try (var outputScope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(SourceOutput.TYPE);
            SourceOutput source = new SourceOutput(3_000_000_001L);
            CompoundTag data = new CompoundTag();

            RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(source, Long.MAX_VALUE)));
            List<MachineOutputAmount> decoded = RecipeOutputCodec.read(data);

            assertThat(decoded).singleElement().satisfies(output -> {
                assertThat(output.output()).isEqualTo(source);
                assertThat(output.amount()).isEqualTo(Long.MAX_VALUE);
                assertThat(output.output().chance()).isEqualTo(1F);
            });
        }
    }
}
