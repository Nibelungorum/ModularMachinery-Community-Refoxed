package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineStructureRequirementsTest {

    private static final Identifier COIL_TYPE = MMCR.id("coil_requirement_test");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        MachineLevelRegistry.beginRegistration();
        if (MachineLevelRegistry.getType(COIL_TYPE) == null) {
            MachineLevelRegistry.registerType(new LevelType(COIL_TYPE, Component.literal("Coil")));
        }
    }

    @Test
    void characterRequirementsExpandToEveryMatchingPositionAndKeepPredicates() {
        BlockPredicate modifierPredicate = new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK);
        SingleBlockModifierReplacement replacement = replacement("speed", modifierPredicate);
        BlockArray pattern = BlockArray.builder()
                .pattern("MLM")
                .set('M', new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))
                .set('L', new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK))
                .build();
        MachineStructureRequirements requirements = MachineStructureRequirements.builder()
                .modifier('M', replacement)
                .levelSlot('L', COIL_TYPE)
                .build(pattern);

        MachineStructureRequirementCompiler.Compiled compiled =
                MachineStructureRequirementCompiler.compile(pattern, requirements);

        assertThat(compiled.modifierReplacements().keySet())
                .containsExactlyInAnyOrder(new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));
        assertThat(compiled.modifierReplacements().values())
                .allSatisfy(replacements -> assertThat(replacements).singleElement()
                        .extracting(SingleBlockModifierReplacement::getReplacement)
                        .isSameAs(modifierPredicate));
        assertThat(compiled.levelSlots()).containsExactly(Map.entry(BlockPos.ZERO, COIL_TYPE));
        assertThat(pattern.pattern().get(new BlockPos(-1, 0, 0)).matches(Blocks.IRON_BLOCK.defaultBlockState())).isTrue();
        assertThat(pattern.pattern().get(BlockPos.ZERO).matches(Blocks.COPPER_BLOCK.defaultBlockState())).isTrue();
    }

    @Test
    void requirementsRejectAbsentCharactersBeforeCompile() {
        BlockArray pattern = BlockArray.builder()
                .pattern("M")
                .set('M', new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))
                .build();

        assertThatThrownBy(() -> MachineStructureRequirements.builder()
                .levelSlot('L', COIL_TYPE)
                .build(pattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L")
                .hasMessageContaining("absent");
    }

    @Test
    void requirementsRejectNullAndConflictingDeclarations() {
        BlockArray pattern = BlockArray.builder()
                .pattern("L")
                .set('L', new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK))
                .build();

        assertThatThrownBy(() -> MachineStructureRequirements.builder().modifier('L', null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("replacement");
        assertThatThrownBy(() -> MachineStructureRequirements.builder()
                .levelSlot('L', COIL_TYPE)
                .levelSlot('L', MMCR.id("other_coil"))
                .build(pattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting level slot")
                .hasMessageContaining("L");
    }

    @Test
    void publicDeclarationAndStageApiDoNotAcceptOrExposePositionBoundRequirementMaps() {
        assertThat(MachineStructureDefinition.Declaration.class.isRecord()).isFalse();
        assertThat(MachineStructureStage.class.isRecord()).isFalse();
        assertThat(publicConstructorSignatures(MachineStructureDefinition.class))
                .noneMatch(MachineStructureRequirementsTest::isPositionBoundRequirementMap);
        assertThat(publicConstructorSignatures(MachineStructureDefinition.Declaration.class))
                .noneMatch(MachineStructureRequirementsTest::isPositionBoundRequirementMap);
        assertThat(publicConstructorSignatures(MachineStructureStage.class))
                .noneMatch(MachineStructureRequirementsTest::isPositionBoundRequirementMap);
    }

    private static SingleBlockModifierReplacement replacement(String name, BlockPredicate predicate) {
        return new SingleBlockModifierReplacement(name, predicate, List.of(), ItemStack.EMPTY);
    }

    private static List<Type> publicConstructorSignatures(Class<?> type) {
        ArrayList<Type> signatures = new ArrayList<>();
        for (var constructor : type.getConstructors()) {
            signatures.addAll(List.of(constructor.getGenericParameterTypes()));
        }
        return List.copyOf(signatures);
    }

    private static boolean isPositionBoundRequirementMap(Type type) {
        if (!(type instanceof ParameterizedType parameterized) || parameterized.getRawType() != Map.class) return false;
        Type[] arguments = parameterized.getActualTypeArguments();
        return arguments.length == 2 && arguments[0] == BlockPos.class
                && (arguments[1] == Identifier.class || isSingleBlockModifierReplacementList(arguments[1]));
    }

    private static boolean isSingleBlockModifierReplacementList(Type type) {
        return type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] == SingleBlockModifierReplacement.class;
    }
}
