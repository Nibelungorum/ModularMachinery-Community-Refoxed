package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.registration.ApiRegistrationException;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.MachineStructureRequirementCompiler;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.ModifierUse;
import cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.definition.StructureRequirements;
import cn.howxu.mmcr.api.machine.definition.StructureStage;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies ID-only optional structure modifier uses.
 *
 * @author howxu <dev@howxu.cn>
 */
class ModifierStructureUseTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void base_and_registered_replacement_states_match_as_optional_modifier_use() {
        ResourceLocation machineId = MMCR.id("modifier_structure");
        ResourceLocation speedupId = MMCR.id("speedup");
        ModifierUse use = ModifierUse.of(speedupId, BlockPredicate.block(Blocks.DIAMOND_BLOCK));
        StructureRegistration event = new StructureRegistration(List.of(machineId));
        event.registerModifier(speedupId, new ModifierDefinition(List.of()));
        event.registerStructure(structure(machineId, use));
        var snapshot = event.freeze();

        var runtime = MachineDefinitionConverter.toStructureDefinition(snapshot.structures().get(machineId));
        var declaration = runtime.declarations().getFirst();
        var compiled = MachineStructureRequirementCompiler.compile(declaration.pattern(), declaration.requirements());
        BlockPos position = BlockPos.ZERO;

        Level baseState = LevelStub.create(Map.of(position, Blocks.BLAST_FURNACE));
        Level replacementState = LevelStub.create(Map.of(position, Blocks.DIAMOND_BLOCK));

        assertThat(StructureMatcher.matchesRotated(declaration.pattern(), baseState, position,
                compiled.modifierReplacements())).isTrue();
        assertThat(foundModifiers(compiled.modifierReplacements(), baseState, position)).isEmpty();
        assertThat(StructureMatcher.matchesRotated(declaration.pattern(), replacementState, position,
                compiled.modifierReplacements())).isTrue();
        assertThat(foundModifiers(compiled.modifierReplacements(), replacementState, position))
                .containsExactly(speedupId.toString());
    }

    @Test
    void unknown_modifier_id_is_rejected_when_structure_event_freezes() {
        ResourceLocation machineId = MMCR.id("unknown_modifier_structure");
        ResourceLocation unknownId = MMCR.id("unknown_speedup");
        StructureRegistration event = new StructureRegistration(List.of(machineId));
        event.registerStructure(structure(machineId,
                ModifierUse.of(unknownId, BlockPredicate.block(Blocks.DIAMOND_BLOCK))));

        assertThatThrownBy(event::freeze)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void modifier_use_symbol_must_exist_in_the_runtime_pattern() {
        ResourceLocation machineId = MMCR.id("missing_modifier_symbol");
        ResourceLocation modifierId = MMCR.id("speedup");
        MachineStructureDefinition structure = new MachineStructureDefinition(machineId, List.of(
                StructureStage.builder()
                        .pattern(pattern -> pattern.layer("M")
                                .where('M', BlockPredicate.block(Blocks.BLAST_FURNACE)).controller('M'))
                        .requirements(requirements -> requirements.modifier('X',
                                ModifierUse.of(modifierId, BlockPredicate.block(Blocks.DIAMOND_BLOCK))))
                        .build()));

        assertThatThrownBy(() -> MachineDefinitionConverter.toStructureDefinition(structure))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Requirement symbol X is absent");
    }

    private static MachineStructureDefinition structure(ResourceLocation machineId, ModifierUse use) {
        return new MachineStructureDefinition(machineId, List.of(
                StructureStage.builder()
                        .pattern(pattern -> pattern.layer("M")
                                .where('M', BlockPredicate.block(Blocks.BLAST_FURNACE)).controller('M'))
                        .modifier('M', use)
                        .build()));
    }

    private static List<String> foundModifiers(
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
            Level level, BlockPos controller) {
        Set<String> ids = new LinkedHashSet<>();
        replacements.forEach((position, values) -> {
            var actual = level.getBlockState(controller.offset(position));
            values.stream()
                    .filter(replacement -> replacement.getReplacement().matches(actual))
                    .map(replacement -> replacement.getModifierId().toString())
                    .forEach(ids::add);
        });
        return new ArrayList<>(ids);
    }
}
