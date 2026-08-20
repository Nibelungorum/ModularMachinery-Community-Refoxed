package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the internal adapter is the only public-to-runtime structure bridge.
 * @author howxu <dev@howxu.cn>
 */
class PublicApiAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void adapters_preserve_definition_structure_and_identifiers() {
        var machineId = MMCR.id("adapter_machine");
        var definition = MachineBuilder.machine(machineId).build();
        MachineStructureDefinition structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .build(machineId);

        assertThat(PublicMachineAdapter.toRegistration(definition).id()).isEqualTo(machineId);
        assertThat(PublicMachineAdapter.toStructureDefinition(structure).machineId()).isEqualTo(machineId);
        assertThat(PublicMachineAdapter.toDynamicMachine(definition, structure).registryName()).isEqualTo(machineId);
    }

    @Test
    void adapter_preserves_complete_definition_and_structure_in_final_dynamic_machine() {
        var machineId = MMCR.id("complete_machine");
        var moduleId = MMCR.id("complete_module");
        var definition = MachineBuilder.machine(machineId)
                .controller(controller -> controller.textures(MMCR.id("block/front"), MMCR.id("block/side")))
                .appearance(appearance -> appearance.machineBasicBlock(MMCR.id("steel_casing")))
                .factory(factory -> factory.hasFactory(true).threadLimit(4)
                        .thread("smelting", MMCR.id("arc_recipe")))
                .role(MachineRole.HOST).acceptedModule(moduleId)
                .maxParallelism(8).parallelizable(true)
                .failureAction(RecipeFailureActions.RESET).build();
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage
                        .pattern(pattern -> pattern.layer("CFC")
                        .where('C', BlockPredicate.block(Blocks.STONE))
                                .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                        .ports(ports -> ports.min("item_input_bus", 1))
                        .portTiers(tiers -> tiers.minItemInput(cn.howxu.mmcr.api.publicapi.machine.PortTiers.ItemTier.NORMAL))
                        .requirements(requirements -> requirements.levelSlot('C', MMCR.id("coil"))))
                .build(machineId);

        var converted = PublicMachineAdapter.toDynamicMachine(definition, structure);
        assertThat(converted.registryName()).isEqualTo(machineId);
        assertThat(converted.controller().frontTexture()).isEqualTo(MMCR.id("block/front"));
        assertThat(converted.appearance().machineBasicBlock()).isEqualTo(MMCR.id("steel_casing"));
        assertThat(converted.portRequirements().requirements()).containsKey("item_input_bus");
        assertThat(converted.portTierRequirements().requirements()).hasSize(1);
        assertThat(converted.maxParallelism()).isEqualTo(8);
        assertThat(converted.parallelizable()).isTrue();
        assertThat(converted.hasFactory()).isTrue();
        assertThat(converted.factoryThreadLimit()).isEqualTo(4);
        assertThat(converted.factoryThreads()).hasSize(1);
        assertThat(converted.role()).isEqualTo(MachineRole.HOST);
        assertThat(converted.acceptedModuleIds()).containsExactly(moduleId);
        assertThat(converted.structureStages()).hasSize(1);
        assertThat(converted.structureStages().getFirst().number()).isEqualTo(1);
        assertThat(converted.structureStages().getFirst().levelSlots()).containsValue(MMCR.id("coil"));
        assertThat(converted.failureAction()).isEqualTo(RecipeFailureActions.RESET);
    }

    @Test
    void adapter_writes_public_structure_to_final_internal_registration() {
        var machineId = MMCR.id("registered_machine");
        var definition = MachineBuilder.machine(machineId).displayNameKey("machine.registered").build();
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .build(machineId);

        var registration = PublicMachineAdapter.toStartupRegistration(definition, structure);
        assertThat(registration.id()).isEqualTo(machineId);
        assertThat(registration.displayNameKey()).isEqualTo("machine.registered");
        assertThat(registration.pattern().get(net.minecraft.core.BlockPos.ZERO)).isNotNull();
    }

    @Test
    void adapter_preserves_extension_stages() {
        var machineId = MMCR.id("extension_machine");
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("S")
                        .where('S', BlockPredicate.block(Blocks.STONE)).controller('S')))
                .build(machineId);

        assertThat(PublicMachineAdapter.toStructureDefinition(structure).declarations())
                .extracting(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration::kind)
                .containsExactly(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.FULL,
                        cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.EXTENSION);
    }

    @Test
    void dynamic_machine_adapter_rejects_structure_for_another_machine() {
        var definition = MachineBuilder.machine(MMCR.id("adapter_definition")).build();
        var structure = structureFor(MMCR.id("other_machine"));

        assertThatThrownBy(() -> PublicMachineAdapter.toDynamicMachine(definition, structure))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("adapter_definition")
                .hasMessageContaining("other_machine");
    }

    @Test
    void startup_registration_adapter_rejects_structure_for_another_machine() {
        var definition = MachineBuilder.machine(MMCR.id("registration_definition")).build();
        var structure = structureFor(MMCR.id("other_machine"));

        assertThatThrownBy(() -> PublicMachineAdapter.toStartupRegistration(definition, structure))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("registration_definition")
                .hasMessageContaining("other_machine");
    }

    private static MachineStructureDefinition structureFor(net.minecraft.resources.Identifier machineId) {
        return MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .build(machineId);
    }
}
