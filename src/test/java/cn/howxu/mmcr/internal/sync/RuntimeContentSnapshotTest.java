package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nibelungorum.DefaultMachineLevels;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeContentSnapshotTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void snapshotDefensivelyCopiesAllMaps() {
        Identifier machineId = MMCR.id("alloy_furnace");
        Map<Identifier, MachineStructureDefinition> structures = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineRecipe> recipes = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineControllerSpec> controllerSpecs = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineAppearanceSpec> appearances = mapWithOpaqueValue(machineId);
        RuntimeContentSnapshot snapshot = new RuntimeContentSnapshot(
                structures, recipes, controllerSpecs, appearances, 7L);

        structures.clear();
        recipes.clear();
        controllerSpecs.clear();
        appearances.clear();

        assertThat(snapshot.structures()).containsKey(machineId);
        assertThat(snapshot.recipes()).containsKey(machineId);
        assertThat(snapshot.controllerSpecs()).containsKey(machineId);
        assertThat(snapshot.appearances()).containsKey(machineId);
        assertThatThrownBy(() -> snapshot.structures().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.recipes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.controllerSpecs().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.appearances().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void structureSyncCodecRoundTripsPatternAndMetadata() {
        MachineStructureDefinition original = structure(MMCR.id("alloy_furnace"));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        MachineStructureSyncCodec.encode(buf, original);
        MachineStructureDefinition decoded = MachineStructureSyncCodec.decode(buf);

        assertThat(decoded.machineId()).isEqualTo(original.machineId());
        assertThat(decoded.declarations()).isEqualTo(original.declarations());
    }

    @Test
    void recipeSyncCodecRoundTripsRuntimeRecipe() {
        MachineRecipe original = new MachineRecipe(
                MMCR.id("sync_recipe"), MMCR.id("alloy_furnace"), 40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(new ItemStack(Items.GOLD_INGOT, 4)),
                List.of(new RecipeModifier("input_bus", RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false)),
                5, 3, true, List.of(), List.of(), true,
                List.of(new LevelRequirement(DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE, DefaultMachineLevels.IRON_COIL)),
                true, Set.of(MMCR.id("runtime_host")));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        MachineRecipeSyncCodec.encode(buf, original);
        MachineRecipe decoded = MachineRecipeSyncCodec.decode(buf);

        assertThat(decoded.id()).isEqualTo(original.id());
        assertThat(decoded.machineId()).isEqualTo(original.machineId());
        assertThat(decoded.tickTime()).isEqualTo(40);
        assertThat(decoded.priority()).isEqualTo(5);
        assertThat(decoded.maxThreads()).isEqualTo(3);
        assertThat(decoded.doesCancelRecipeOnPerTickFailure()).isTrue();
        assertThat(decoded.isParallelized()).isTrue();
        assertThat(decoded.allowPartialOutputs()).isTrue();
        assertThat(decoded.requiredHostIds()).containsExactly(MMCR.id("runtime_host"));
        assertThat(decoded.levelRequirements()).containsExactlyElementsOf(original.levelRequirements());
        assertThat(decoded.modifiers()).containsExactlyElementsOf(original.modifiers());
        assertThat(decoded.inputs()).containsExactlyElementsOf(original.inputs());
        assertThat(decoded.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.GOLD_INGOT);
            assertThat(stack.getCount()).isEqualTo(4);
        });
        assertThat(decoded.requirements()).hasSameSizeAs(original.requirements());
        assertThat(decoded.requirements().getFirst()).satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(ItemRequirement.class);
            ItemRequirement item = (ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(RecipeModifier.IOType.INPUT);
            assertThat(item.count()).isEqualTo(2);
        });
        assertThat(decoded.requirements().get(1)).satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(ItemRequirement.class);
            ItemRequirement item = (ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(item.stack().getItem()).isEqualTo(Items.GOLD_INGOT);
            assertThat(item.stack().getCount()).isEqualTo(4);
        });
    }

    @Test
    void structureSyncCodecRejectsOversizedDeclarationCountOnEncode() {
        List<MachineStructureDefinition.Declaration> declarations = java.util.Collections.nCopies(1025,
                structure(MMCR.id("alloy_furnace")).declarations().getFirst());
        MachineStructureDefinition original = new MachineStructureDefinition(MMCR.id("oversized"), declarations);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        assertThatThrownBy(() -> MachineStructureSyncCodec.encode(buf, original))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid declaration count");
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<Identifier, T> mapWithOpaqueValue(Identifier id) {
        Map<Identifier, T> map = new LinkedHashMap<>();
        map.put(id, (T) new Object());
        return map;
    }

    private static MachineStructureDefinition structure(Identifier id) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE));
        pattern.put(BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
        BlockArray blockArray = new BlockArray(pattern).tagged(BlockPos.ZERO.east(), "input_bus");
        PortRequirementSpec portRequirements = PortRequirementSpec.builder().min("item_input_bus", 1).build();
        return new MachineStructureDefinition(id, blockArray, portRequirements,
                cn.howxu.mmcr.api.machine.PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of(
                BlockPos.ZERO.east(), MMCR.id("coil")));
    }
}
