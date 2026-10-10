package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.PatternDefinition;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.machine.definition.StructureStage;
import cn.howxu.mmcr.internal.api.facade.structure.TierAdapters;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.internal.port.IOPortKind;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end tier conversion and structure sync preserve appended source categories.
 * @author howxu <dev@howxu.cn>
 */
class SourcePortTierSyncTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void public_source_limits_convert_through_machine_declarations_and_sync_unchanged() {
        var limits = PortTierLimits.builder().anySourceInput().anySourceOutput().anyEnergyInput().build();
        assertThat(limits.requirements()).extracting(PortTierLimits.RequirementView::category).containsExactly(
                PortTierLimits.PortCategory.SOURCE, PortTierLimits.PortCategory.SOURCE, PortTierLimits.PortCategory.ENERGY);
        assertThat(limits.requirements()).extracting(PortTierLimits.RequirementView::ioType)
                .containsExactly(IoDirection.INPUT, IoDirection.OUTPUT, IoDirection.INPUT);
        PortTiers tiers = TierAdapters.unwrap(limits);
        var pattern = new PatternDefinition(List.of(List.of("C")), Map.of('C', BlockPredicate.block(Blocks.STONE)),
                'C', 1, 1, 1);
        var stage = new StructureStage(StructureStage.Kind.FULL, pattern, null, tiers, null);
        var declaration = MachineDefinitionConverter.toDeclaration(stage);
        assertThat(declaration.portTierRequirements()).isEqualTo(PortTierRequirementSpec.from(tiers));
        var original = new MachineStructureDefinition(ResourceLocation.parse("test:source_sync"), List.of(declaration));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MachineStructureSyncCodec.encode(buffer, original);
            var decoded = MachineStructureSyncCodec.decode(buffer);
            assertThat(decoded.declarations()).isEqualTo(original.declarations());
            assertThat(decoded.portTierRequirements().requirements()).containsExactly(
                    new PortTierRequirementSpec.Requirement(PortTierRequirementSpec.PortCategory.SOURCE, IOType.INPUT, 0, "normal"),
                    new PortTierRequirementSpec.Requirement(PortTierRequirementSpec.PortCategory.SOURCE, IOType.OUTPUT, 0, "normal"),
                    new PortTierRequirementSpec.Requirement(PortTierRequirementSpec.PortCategory.ENERGY, IOType.INPUT, 0, "tiny"));
            assertThat(buffer.isReadable()).isFalse();
        } finally {
            buffer.release();
        }
    }

    @Test
    void old_category_wire_ordinals_stay_stable_and_source_is_appended() {
        assertThat(PortTiers.PortCategory.values()).startsWith(PortTiers.PortCategory.ITEM,
                PortTiers.PortCategory.FLUID, PortTiers.PortCategory.ENERGY, PortTiers.PortCategory.SOURCE, PortTiers.PortCategory.MANA);
        assertThat(PortTierLimits.PortCategory.values()).startsWith(PortTierLimits.PortCategory.ITEM,
                PortTierLimits.PortCategory.FLUID, PortTierLimits.PortCategory.ENERGY, PortTierLimits.PortCategory.SOURCE, PortTierLimits.PortCategory.MANA);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            for (var category : List.of(PortTierRequirementSpec.PortCategory.ITEM, PortTierRequirementSpec.PortCategory.FLUID,
                    PortTierRequirementSpec.PortCategory.ENERGY, PortTierRequirementSpec.PortCategory.SOURCE,
                    PortTierRequirementSpec.PortCategory.MANA)) buffer.writeEnum(category);
            assertThat(buffer.readVarInt()).isZero();
            assertThat(buffer.readVarInt()).isEqualTo(1);
            assertThat(buffer.readVarInt()).isEqualTo(2);
            assertThat(buffer.readVarInt()).isEqualTo(3);
            assertThat(buffer.readVarInt()).isEqualTo(4);
        } finally {
            buffer.release();
        }
        var tiers = PortTierRequirementSpec.builder().anyItemInput().anyFluidOutput().anyEnergyInput().build();
        var structure = new MachineStructureDefinition(ResourceLocation.parse("test:legacy_tier_sync"), new BlockArray(Map.of()),
                PortRequirementSpec.none(), tiers, List.of(), MachineStructureRequirements.EMPTY);
        var legacy = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MachineStructureSyncCodec.encode(legacy, structure);
            assertThat(MachineStructureSyncCodec.decode(legacy).portTierRequirements()).isEqualTo(tiers);
        } finally {
            legacy.release();
        }
    }

    @Test
    void public_mixed_constraints_survive_conversion_and_structure_sync() {
        var limits = PortTierLimits.builder().anyItemInput().anyFluidOutput().anyEnergyInput()
                .anySourceInput().anyManaOutput()
                .minChemicalInput(PortTierLimits.ChemicalTier.ADVANCED).minChemicalOutput(PortTierLimits.ChemicalTier.ULTIMATE)
                .anyRadioactiveChemicalInput().anyRadioactiveChemicalOutput().anyHeatInput().anyHeatOutput()
                .anyStressInput().anyStressOutput().anyAirInput().anyAirOutput().build();
        var counts = PortRequirementSpec.builder().range("chemical_input_hatch", 1, 2)
                .min("radioactive_chemical_output_hatch", 1).range("heat_input_hatch", 0, 1)
                .min("create_stress_input_interface", 1).min("pneumaticcraft_air_output_interface", 1).build();
        var original = new MachineStructureDefinition(ResourceLocation.parse("test:mixed_port_sync"), new BlockArray(Map.of()),
                counts, PortTierRequirementSpec.from(TierAdapters.unwrap(limits)), List.of(), MachineStructureRequirements.EMPTY);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MachineStructureSyncCodec.encode(buffer, original);
            var decoded = MachineStructureSyncCodec.decode(buffer);
            assertThat(decoded.declarations()).isEqualTo(original.declarations());
            assertThat(decoded.portTierRequirements().validate(List.<IOPortKind>of()))
                    .isEqualTo(original.portTierRequirements().validate(List.<IOPortKind>of()));
            assertThat(buffer.isReadable()).isFalse();
        } finally {
            buffer.release();
        }
    }

    @Test
    void structure_sync_rejects_fabricated_tiers_during_decode() throws Exception {
        var read = MachineStructureSyncCodec.class.getDeclaredMethod("readPortTierRequirements", RegistryFriendlyByteBuf.class);
        read.setAccessible(true);
        for (var category : List.of(PortTierRequirementSpec.PortCategory.CHEMICAL,
                PortTierRequirementSpec.PortCategory.RADIOACTIVE_CHEMICAL, PortTierRequirementSpec.PortCategory.HEAT,
                PortTierRequirementSpec.PortCategory.STRESS, PortTierRequirementSpec.PortCategory.AIR)) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                buffer.writeVarInt(1);
                buffer.writeEnum(category);
                buffer.writeEnum(IOType.INPUT);
                buffer.writeVarInt(category == PortTierRequirementSpec.PortCategory.CHEMICAL ? 0 : 8);
                ByteBufCodecs.STRING_UTF8.encode(buffer, category == PortTierRequirementSpec.PortCategory.CHEMICAL ? "ultimate" : "any");
                assertThatThrownBy(() -> read.invoke(null, buffer)).hasCauseInstanceOf(IllegalArgumentException.class);
            } finally {
                buffer.release();
            }
        }
    }
}
