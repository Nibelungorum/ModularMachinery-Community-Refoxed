package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineStructureFamilyTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void legacyDefinitionBecomesOneFullStage() {
        BlockArray base = array(Map.of(BlockPos.ZERO, controller(), new BlockPos(1, 0, 0), casing()));
        MachineStructureDefinition definition = new MachineStructureDefinition(
                MMCR.id("legacy"), base, PortRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        MachineStructureFamily family = MachineStructureFamily.of(definition);

        assertThat(family.stages()).hasSize(1);
        assertThat(family.stages().getFirst().number()).isEqualTo(1);
        assertThat(family.stages().getFirst().pattern()).isEqualTo(base);
    }

    @Test
    void fullAndExtensionDeclarationsProduceIndependentFullSnapshots() {
        BlockArray base = array(Map.of(BlockPos.ZERO, controller(), new BlockPos(1, 0, 0), casing()));
        BlockArray vertical = array(Map.of(new BlockPos(1, 1, 0), casing()));
        BlockArray horizontalReset = array(Map.of(BlockPos.ZERO, controller(), new BlockPos(-1, 0, 0), casing()));
        BlockArray horizontalExtension = array(Map.of(new BlockPos(-2, 0, 0), casing()));
        MachineStructureDefinition definition = definition("mixed",
                Declaration.full(base), Declaration.extension(vertical),
                Declaration.full(horizontalReset), Declaration.extension(horizontalExtension));

        List<MachineStructureStage> stages = MachineStructureFamily.of(definition).stages();

        assertThat(stages).extracting(MachineStructureStage::number).containsExactly(1, 2, 3, 4);
        assertThat(stages.get(1).pattern().pattern().keySet())
                .containsExactlyInAnyOrder(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(1, 1, 0));
        assertThat(stages.get(3).pattern().pattern().keySet())
                .containsExactlyInAnyOrder(BlockPos.ZERO, new BlockPos(-1, 0, 0), new BlockPos(-2, 0, 0));
        assertThat(stages.getFirst().pattern().pattern()).hasSize(2);
    }

    @Test
    void stagesRememberTheDeclarationKindThatCreatedThem() {
        BlockArray base = array(Map.of(BlockPos.ZERO, controller(), new BlockPos(1, 0, 0), casing()));
        BlockArray extension = array(Map.of(new BlockPos(2, 0, 0), casing()));

        List<MachineStructureStage> stages = MachineStructureFamily.of(definition("stage_kind",
                Declaration.full(base), Declaration.extension(extension))).stages();

        assertThat(stages).extracting(MachineStructureStage::kind)
                .containsExactly(Declaration.Kind.FULL, Declaration.Kind.EXTENSION);
    }

    @Test
    void extensionMergesEquivalentPredicatesAndTags() {
        BlockPos casingPos = new BlockPos(1, 0, 0);
        BlockArray base = array(Map.of(BlockPos.ZERO, controller(), casingPos, casing())).tagged(casingPos, "shell");
        BlockArray extension = array(Map.of(casingPos, casing(), new BlockPos(2, 0, 0), casing()))
                .tagged(casingPos, "shell");

        MachineStructureStage merged = MachineStructureFamily.of(definition("equivalent",
                Declaration.full(base), Declaration.extension(extension))).stages().getLast();

        assertThat(merged.pattern().pattern()).hasSize(3);
        assertThat(merged.pattern().tagsAt(casingPos)).containsExactly("shell");
    }

    @Test
    void extensionInheritsOmittedPortRequirementsAndReplacesExplicitOnes() {
        PortRequirementSpec basePorts = PortRequirementSpec.builder().min("item_input", 1).build();
        PortRequirementSpec replacementPorts = PortRequirementSpec.builder().min("energy_input", 2).build();
        Declaration base = new Declaration(Declaration.Kind.FULL,
                array(Map.of(BlockPos.ZERO, controller())), basePorts, PortTierRequirementSpec.none(),
                List.of(), MachineStructureRequirements.EMPTY);
        Declaration inherited = Declaration.extension(array(Map.of(new BlockPos(1, 0, 0), casing())));
        Declaration replaced = new Declaration(Declaration.Kind.EXTENSION,
                array(Map.of(new BlockPos(2, 0, 0), casing())), replacementPorts, null,
                List.of(), MachineStructureRequirements.EMPTY);

        List<MachineStructureStage> stages = MachineStructureFamily.of(
                definition("requirements", base, inherited, replaced)).stages();

        assertThat(stages.get(1).portRequirements()).isEqualTo(basePorts);
        assertThat(stages.get(2).portRequirements()).isEqualTo(replacementPorts);
        assertThat(stages.get(2).portTierRequirements()).isEqualTo(PortTierRequirementSpec.none());
    }

    @Test
    void extensionMergesCharacterRequirementsWithoutReplacingExistingOnes() {
        SingleBlockModifierReplacement baseReplacement = replacement("base", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK));
        SingleBlockModifierReplacement extensionReplacement = replacement("extension", new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK));
        Declaration base = new Declaration(Declaration.Kind.FULL,
                BlockArray.builder().pattern("CM").set('C', controller()).set('M', casing()).build(),
                null, null, List.of(),
                MachineStructureRequirements.builder().modifier('M', baseReplacement).levelSlot('M', MMCR.id("base_coil")).build());
        Declaration extension = new Declaration(Declaration.Kind.EXTENSION,
                characterArray(new BlockPos(2, 0, 0), 'M', casing()),
                null, null, List.of(),
                MachineStructureRequirements.builder().modifier('M', extensionReplacement).build());

        MachineStructureStage stage = MachineStructureFamily.of(definition("requirements_merge", base, extension))
                .stages().getLast();

        assertThat(stage.requirements().modifierReplacements().get('M'))
                .containsExactly(baseReplacement, extensionReplacement);
        assertThat(stage.requirements().levelSlots()).containsEntry('M', MMCR.id("base_coil"));
        assertThat(stage.modifierReplacements()).hasSize(2);
        assertThat(stage.levelSlots()).containsEntry(new BlockPos(1, 0, 0), MMCR.id("base_coil"));
    }

    @Test
    void extensionRejectsConflictingCharacterLevelRequirement() {
        Declaration base = new Declaration(Declaration.Kind.FULL,
                BlockArray.builder().pattern("CM").set('C', controller()).set('M', casing()).build(),
                null, null, List.of(),
                MachineStructureRequirements.builder().levelSlot('M', MMCR.id("base_coil")).build());
        Declaration extension = new Declaration(Declaration.Kind.EXTENSION,
                characterArray(new BlockPos(2, 0, 0), 'M', casing()),
                null, null, List.of(),
                MachineStructureRequirements.builder().levelSlot('M', MMCR.id("other_coil")).build());

        assertThatThrownBy(() -> MachineStructureFamily.of(definition("requirements_conflict", base, extension)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage 2")
                .hasMessageContaining("conflicting level slot")
                .hasMessageContaining("M");
    }

    @Test
    void stageSnapshotsDefensivelyCopyCoordinateMetadata() {
        BlockPos pos = new BlockPos(1, 0, 0);
        List<SingleBlockModifierReplacement> replacements = new ArrayList<>();
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacementMap = new LinkedHashMap<>();
        replacementMap.put(pos, replacements);
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
        levelSlots.put(pos, MMCR.id("coil"));
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, controller(), pos, casing()),
                Map.of(), Map.of(BlockPos.ZERO, 'C', pos, 'M'));
        Declaration declaration = new Declaration(Declaration.Kind.FULL, pattern, null, null, List.of(),
                MachineStructureRequirements.builder().modifier('M', replacement("speed", casing()))
                        .levelSlot('M', MMCR.id("coil")).build(pattern));

        MachineStructureStage stage = MachineStructureFamily.of(definition("immutable", declaration)).stages().getFirst();
        replacements.add(null);
        replacementMap.clear();
        levelSlots.clear();

        assertThat(stage.modifierReplacements()).containsKey(pos);
        assertThat(stage.modifierReplacements().get(pos)).singleElement()
                .extracting(SingleBlockModifierReplacement::getModifierName)
                .isEqualTo("speed");
        assertThat(stage.levelSlots()).containsEntry(pos, MMCR.id("coil"));
        assertThatThrownBy(() -> stage.modifierReplacements().put(pos, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void declarationsDefensivelyCopyPatterns() {
        Map<BlockPos, BlockPredicate> mutablePattern = new LinkedHashMap<>();
        mutablePattern.put(BlockPos.ZERO, controller());
        Declaration declaration = Declaration.full(new BlockArray(mutablePattern));
        mutablePattern.put(new BlockPos(1, 0, 0), casing());

        MachineStructureStage stage = MachineStructureFamily.of(definition("declaration_snapshot", declaration))
                .stages().getFirst();

        assertThat(stage.pattern().pattern()).containsOnlyKeys(BlockPos.ZERO);
        assertThatThrownBy(() -> declaration.pattern().pattern().put(new BlockPos(2, 0, 0), casing()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void firstDeclarationMustBeFull() {
        assertThatThrownBy(() -> MachineStructureFamily.of(definition("starts_extension",
                Declaration.extension(array(Map.of(new BlockPos(1, 0, 0), casing()))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage 1 must be a full structure");
    }

    @Test
    void fullDeclarationRejectsMultipleControllers() {
        BlockArray invalid = array(Map.of(
                BlockPos.ZERO, controller(),
                new BlockPos(2, 0, 0), controller()));

        assertThatThrownBy(() -> MachineStructureFamily.of(definition("multiple_controllers",
                Declaration.full(invalid))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage 1")
                .hasMessageContaining("controller");
    }

    @Test
    void extensionRejectsConflictingPredicate() {
        BlockArray base = array(Map.of(BlockPos.ZERO, controller(), new BlockPos(1, 0, 0), casing()));

        assertThatThrownBy(() -> MachineStructureFamily.of(definition("conflict",
                Declaration.full(base),
                Declaration.extension(array(Map.of(new BlockPos(1, 0, 0), otherCasing()))))))
                .hasMessageContaining("stage 2")
                .hasMessageContaining("1, 0, 0");
    }

    @Test
    void extensionRejectsSecondController() {
        BlockArray base = array(Map.of(BlockPos.ZERO, controller(), new BlockPos(1, 0, 0), casing()));

        assertThatThrownBy(() -> MachineStructureFamily.of(definition("second_controller",
                Declaration.full(base),
                Declaration.extension(array(Map.of(new BlockPos(2, 0, 0), controller()))))))
                .hasMessageContaining("stage 2")
                .hasMessageContaining("controller");
    }

    @Test
    void fullDeclarationRejectsOfBlockStateController() {
        BlockArray invalid = array(Map.of(
                BlockPos.ZERO, controller(),
                new BlockPos(2, 0, 0), controllerState()));

        assertThatThrownBy(() -> MachineStructureFamily.of(definition("of_block_state_controller",
                Declaration.full(invalid))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage 1")
                .hasMessageContaining("2, 0, 0")
                .hasMessageContaining("controller");
    }

    @Test
    void extensionRejectsAnyOfController() {
        BlockArray base = array(Map.of(BlockPos.ZERO, controller(), new BlockPos(1, 0, 0), casing()));

        assertThatThrownBy(() -> MachineStructureFamily.of(definition("any_of_controller",
                Declaration.full(base),
                Declaration.extension(array(Map.of(new BlockPos(3, 0, 0), anyOfController()))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage 2")
                .hasMessageContaining("3, 0, 0")
                .hasMessageContaining("controller");
    }

    private static MachineStructureDefinition definition(String name, Declaration... declarations) {
        return new MachineStructureDefinition(MMCR.id(name), List.of(declarations));
    }

    private static BlockArray array(Map<BlockPos, BlockPredicate> pattern) {
        return new BlockArray(pattern);
    }

    private static BlockArray characterArray(BlockPos position, char symbol, BlockPredicate predicate) {
        return new BlockArray(Map.of(position, predicate), Map.of(), Map.of(position, symbol));
    }

    private static BlockPredicate controller() {
        return new BlockPredicate.OfBlock(ModBlocks.controllerFor(MMCR.id("blast_furnace")).get());
    }

    private static BlockPredicate controllerState() {
        return new BlockPredicate.OfBlockState(ModBlocks.controllerFor(MMCR.id("blast_furnace")).get().defaultBlockState());
    }

    private static BlockPredicate anyOfController() {
        return new BlockPredicate.AnyOf(List.of(casing(), controllerState()));
    }

    private static BlockPredicate casing() {
        return new BlockPredicate.OfBlock(Blocks.STONE);
    }

    private static BlockPredicate otherCasing() {
        return new BlockPredicate.OfBlock(Blocks.DEEPSLATE);
    }

    private static SingleBlockModifierReplacement replacement(String name, BlockPredicate predicate) {
        return new SingleBlockModifierReplacement(name, predicate, List.of(), ItemStack.EMPTY);
    }
}
