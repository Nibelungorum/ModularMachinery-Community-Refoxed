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
                MMCR.id("legacy"), base, PortRequirementSpec.none(), List.of(), Map.of());

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
                List.of(), Map.of(), Map.of());
        Declaration inherited = Declaration.extension(array(Map.of(new BlockPos(1, 0, 0), casing())));
        Declaration replaced = new Declaration(Declaration.Kind.EXTENSION,
                array(Map.of(new BlockPos(2, 0, 0), casing())), replacementPorts, null,
                List.of(), Map.of(), Map.of());

        List<MachineStructureStage> stages = MachineStructureFamily.of(
                definition("requirements", base, inherited, replaced)).stages();

        assertThat(stages.get(1).portRequirements()).isEqualTo(basePorts);
        assertThat(stages.get(2).portRequirements()).isEqualTo(replacementPorts);
        assertThat(stages.get(2).portTierRequirements()).isEqualTo(PortTierRequirementSpec.none());
    }

    @Test
    void stageSnapshotsDefensivelyCopyCoordinateMetadata() {
        BlockPos pos = new BlockPos(1, 0, 0);
        List<SingleBlockModifierReplacement> replacements = new ArrayList<>();
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacementMap = new LinkedHashMap<>();
        replacementMap.put(pos, replacements);
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
        levelSlots.put(pos, MMCR.id("coil"));
        Declaration declaration = new Declaration(Declaration.Kind.FULL,
                array(Map.of(BlockPos.ZERO, controller(), pos, casing())), null, null,
                List.of(), replacementMap, levelSlots);

        MachineStructureStage stage = MachineStructureFamily.of(definition("immutable", declaration)).stages().getFirst();
        replacements.add(null);
        replacementMap.clear();
        levelSlots.clear();

        assertThat(stage.modifierReplacements()).containsKey(pos);
        assertThat(stage.modifierReplacements().get(pos)).isEmpty();
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

    private static MachineStructureDefinition definition(String name, Declaration... declarations) {
        return new MachineStructureDefinition(MMCR.id(name), List.of(declarations));
    }

    private static BlockArray array(Map<BlockPos, BlockPredicate> pattern) {
        return new BlockArray(pattern);
    }

    private static BlockPredicate controller() {
        return new BlockPredicate.OfBlock(ModBlocks.CONTROLLER.get());
    }

    private static BlockPredicate casing() {
        return new BlockPredicate.OfBlock(Blocks.STONE);
    }

    private static BlockPredicate otherCasing() {
        return new BlockPredicate.OfBlock(Blocks.DEEPSLATE);
    }
}
