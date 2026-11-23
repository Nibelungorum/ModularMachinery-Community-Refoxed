package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.ClientTooltipFlag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies blueprint material summaries.
 *
 * @author howxu <dev@howxu.cn>
 */
class BlueprintItemTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModDataComponents.BLUEPRINT_MACHINE, DataComponentType.<ResourceLocation>builder()
                .persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build());
        bind(ModDataComponents.BLUEPRINT_STAGE, DataComponentType.<Integer>builder().persistent(Codec.INT).build());
    }

    @Test
    void requirementsUseLowestPriorityLevel() throws Exception {
        ResourceLocation levelType = MMCR.id("blueprint_test_level");
        Collection<LevelType> previousTypes = MachineLevelRegistry.types();
        Collection<MachineLevel> previousLevels = previousTypes.stream()
                .flatMap(type -> MachineLevelRegistry.getLevels(type.id()).stream())
                .toList();
        MachineLevelRegistry.installSnapshot(List.of(new LevelType(levelType, Component.literal("Test"))), List.of(
                level(MMCR.id("blueprint_high"), levelType, 10, Blocks.IRON_BLOCK),
                level(MMCR.id("blueprint_low"), levelType, 1, Blocks.COPPER_BLOCK)));
        try {
            BlockPos slot = new BlockPos(1, 0, 0);
            BlockArray pattern = new BlockArray(Map.of(
                    BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                    slot, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)), Map.of(),
                    Map.of(BlockPos.ZERO, 'C', slot, 'L'));
            MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                    PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                    .levelSlot('L', levelType).build(pattern));

            List<ItemStack> requirements = requirements(new TestMachine(List.of(stage)));

            assertThat(requirements).singleElement().satisfies(stack ->
                    assertThat(stack.is(Blocks.COPPER_BLOCK.asItem())).isTrue());
        } finally {
            MachineLevelRegistry.installSnapshot(previousTypes, previousLevels);
        }
    }

    @Test
    void expandedTooltipFollowsTheStageSavedOnEachBlueprint() {
        BlockPos baseBlock = new BlockPos(1, 0, 0);
        BlockArray basePattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                baseBlock, new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK)), Map.of(),
                Map.of(BlockPos.ZERO, 'C', baseBlock, 'B'));
        BlockPos extensionBlock = new BlockPos(2, 0, 0);
        BlockArray extensionPattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                extensionBlock, new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK)), Map.of(),
                Map.of(BlockPos.ZERO, 'C', extensionBlock, 'E'));
        ResourceLocation id = MMCR.id("expandable_structure_stages");
        var previous = MachineStructureRegistry.effectiveSnapshot();
        try {
            MachineRegistry.installStructures(Map.of(id, new MachineStructureDefinition(id,
                    List.of(Declaration.full(basePattern), Declaration.extension(extensionPattern)))));
            ItemStack base = new ItemStack(ModItems.BLUEPRINT.get());
            base.set(ModDataComponents.BLUEPRINT_MACHINE.get(), id);
            ItemStack upgraded = base.copy();
            upgraded.set(ModDataComponents.BLUEPRINT_STAGE.get(), 2);

            String copper = "1 " + Blocks.COPPER_BLOCK.getName().getString();
            String diamond = "1 " + Blocks.DIAMOND_BLOCK.getName().getString();
            assertThat(tooltipMaterials(base)).containsExactly(copper);
            assertThat(tooltipMaterials(upgraded)).containsExactlyInAnyOrder(copper, diamond);
            assertThat(tooltipMaterials(base)).containsExactly(copper);
            upgraded.set(ModDataComponents.BLUEPRINT_STAGE.get(), 99);
            assertThat(tooltipMaterials(upgraded)).containsExactly(copper);
        } finally {
            MachineRegistry.installStructures(previous);
        }
    }

    @Test
    void boundBlueprintUsesMachineNameAndEnchantmentEffect() {
        ItemStack blueprint = new ItemStack(ModItems.BLUEPRINT.get());
        blueprint.set(ModDataComponents.BLUEPRINT_MACHINE.get(), MMCR.id("test_cube"));
        blueprint.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        Machine machine = MachineRegistry.getMachine(MMCR.id("test_cube"));

        assertThat(ModItems.BLUEPRINT.get().getName(blueprint)).isEqualTo(machine.displayName());
        assertThat(ModItems.BLUEPRINT.get().isFoil(blueprint)).isTrue();
    }

    @Test
    void boundBlueprintStartsTooltipWithAquaRecipeListTitle() {
        ItemStack blueprint = new ItemStack(ModItems.BLUEPRINT.get());
        blueprint.set(ModDataComponents.BLUEPRINT_MACHINE.get(), MMCR.id("test_cube"));
        List<Component> tooltip = new java.util.ArrayList<>();

        ModItems.BLUEPRINT.get().appendHoverText(blueprint, Item.TooltipContext.EMPTY,
                tooltip, TooltipFlag.NORMAL);

        assertThat(tooltip).isNotEmpty();
        assertThat(tooltip.getFirst().getContents()).isInstanceOf(TranslatableContents.class);
        assertThat(((TranslatableContents) tooltip.getFirst().getContents()).getKey())
                .isEqualTo("tooltip.mmcr.blueprint.recipe_list_collapsed");
        assertThat(tooltip.getFirst().getStyle().getColor().getValue()).isEqualTo(ChatFormatting.AQUA.getColor());
    }

    private static List<String> tooltipMaterials(ItemStack blueprint) {
        List<Component> tooltip = new ArrayList<>();
        ModItems.BLUEPRINT.get().appendHoverText(blueprint, Item.TooltipContext.EMPTY,
                tooltip, new ClientTooltipFlag(false, false, true, false, false));
        return tooltip.stream().skip(1).map(Component::getString).toList();
    }

    private static MachineLevel level(ResourceLocation id, ResourceLocation type, int priority, net.minecraft.world.level.block.Block block) {
        return new MachineLevel(id, type, priority, new BlockPredicate.OfBlockState(block.defaultBlockState()),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
    }

    private static void bind(Object deferredHolder, Object value) throws Exception {
        Field holder = null;
        for (Class<?> type = deferredHolder.getClass(); type != null && holder == null; type = type.getSuperclass()) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(value));
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> requirements(Machine machine) throws Exception {
        Method method = BlueprintItem.class.getDeclaredMethod("requirements", Machine.class, int.class);
        method.setAccessible(true);
        return (List<ItemStack>) method.invoke(null, machine, 0);
    }

    private record TestMachine(List<MachineStructureStage> stages) implements Machine {
        @Override
        public ResourceLocation registryName() {
            return MMCR.id("blueprint_test_machine");
        }

        @Override
        public BlockArray pattern() {
            return stages.getFirst().pattern();
        }

        @Override
        public MachineControllerSpec controller() {
            return MachineControllerSpec.defaultsFor(registryName());
        }

        @Override
        public List<MachineStructureStage> structureStages() {
            return stages;
        }
    }
}
