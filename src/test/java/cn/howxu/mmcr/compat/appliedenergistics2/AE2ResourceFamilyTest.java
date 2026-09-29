package cn.howxu.mmcr.compat.appliedenergistics2;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AE2ResourceFamilyTest {
    @Test
    void nativeFamiliesRetainTheirDirectionTierAndAliasContracts() {
        assertThat(AE2ResourceFamilies.inputFamilies())
                .extracting(PortFamilyDescriptor::familyId, PortFamilyDescriptor::ioType,
                        PortFamilyDescriptor::detectionTier, PortFamilyDescriptor::countAliases)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PortFamilyIds.ITEM, IOType.INPUT,
                                ItemBusSize.values().length, java.util.List.of("item_input_bus")),
                        org.assertj.core.groups.Tuple.tuple(PortFamilyIds.FLUID, IOType.INPUT,
                                FluidHatchSize.values().length, java.util.List.of("fluid_input_hatch")));
        assertThat(AE2ResourceFamilies.outputFamilies())
                .extracting(PortFamilyDescriptor::familyId, PortFamilyDescriptor::ioType,
                        PortFamilyDescriptor::detectionTier, PortFamilyDescriptor::countAliases)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PortFamilyIds.ITEM, IOType.OUTPUT,
                                ItemBusSize.LUDICROUS.ordinal() + 1, java.util.List.of("item_output_bus")),
                        org.assertj.core.groups.Tuple.tuple(PortFamilyIds.FLUID, IOType.OUTPUT,
                                FluidHatchSize.VACUUM.ordinal() + 1, java.util.List.of("fluid_output_hatch")));
    }

    @Test
    void patternFamiliesComposeNativeInputsThenOutputs() {
        assertThat(AE2ResourceFamilies.patternFamilies())
                .containsExactlyElementsOf(java.util.stream.Stream.concat(
                        AE2ResourceFamilies.inputFamilies().stream(),
                        AE2ResourceFamilies.outputFamilies().stream()).toList());
    }
}
