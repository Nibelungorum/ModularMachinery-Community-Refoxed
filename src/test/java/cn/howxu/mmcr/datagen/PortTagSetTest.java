package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourcePortKind;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortTagSetTest {
    @Test
    void sourceInterfacesDeriveOptionalSourceTagsWithoutEnergyTags() {
        for (IOType direction : IOType.values()) {
            String id = direction == IOType.INPUT ? ArsSourceIds.INPUT : ArsSourceIds.OUTPUT;
            PortTagSet tags = PortTagSet.forKind(new SourcePortKind(id, direction));

            assertEquals(List.of(MMCR.id("ports"), MMCR.id("machines"),
                    MMCR.id("ars_nouveau_source_ports"),
                    MMCR.id("ars_nouveau_source_" + direction.getSerializedName() + "_ports"),
                    MMCR.id("ars_nouveau_ports")), tags.tags());
            assertTrue(tags.optionalEntries());
        }
    }

    @Test
    void classifiesBuiltInItemInputPort() {
        PortTagSet tags = PortTagSet.forKind(PortKinds.ITEM_INPUT);

        assertEquals(List.of(MMCR.id("ports"), MMCR.id("machines"), MMCR.id("item_ports"),
                MMCR.id("item_input_ports")), tags.tags());
        assertFalse(tags.optionalEntries());
    }

    @Test
    void classifiesOptionalExternalPort() {
        IOPortKind kind = new TestPortKind("test_port", IOType.INPUT,
                List.of(new PortFamilyDescriptor(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical"),
                        IOType.INPUT, 0, List.of())), List.of("mekanism"));

        PortTagSet tags = PortTagSet.forKind(kind);

        assertTrue(tags.tags().contains(MMCR.id("ports")));
        assertTrue(tags.tags().contains(MMCR.id("mekanism_chemical_ports")));
        assertTrue(tags.tags().contains(MMCR.id("mekanism_chemical_input_ports")));
        assertTrue(tags.tags().contains(MMCR.id("mekanism_ports")));
        assertTrue(tags.optionalEntries());
    }

    @Test
    void mixedMePatternFamiliesDeriveOrdinaryChemicalTagsInBothDirections() {
        ResourceLocation chemical = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical");
        IOPortKind kind = new TestPortKind("test_me_pattern", IOType.INPUT, List.of(
                new PortFamilyDescriptor(MMCR.id("item"), IOType.INPUT, 0, List.of()),
                new PortFamilyDescriptor(chemical, IOType.INPUT, 0, List.of()),
                new PortFamilyDescriptor(chemical, IOType.OUTPUT, 0, List.of())), List.of("ae2"));
        PortTagSet tags = PortTagSet.forKind(kind);
        assertTrue(tags.tags().containsAll(List.of(MMCR.id("mekanism_chemical_ports"),
                MMCR.id("mekanism_chemical_input_ports"), MMCR.id("mekanism_chemical_output_ports"))));
        assertFalse(tags.tags().contains(MMCR.id("mekanism_radioactive_chemical_ports")));
        assertTrue(tags.optionalEntries());
    }

    @Test
    void mixedMeFamiliesDeriveRadioactiveChemicalTagsInBothDirections() {
        ResourceLocation radioactive = MMCR.id("mekanism_radioactive_chemical");
        IOPortKind kind = new TestPortKind("test_me_pattern", IOType.INPUT, List.of(
                new PortFamilyDescriptor(ResourceLocation.parse("mekanism:chemical"), IOType.INPUT, 0, List.of()),
                new PortFamilyDescriptor(radioactive, IOType.INPUT, 0, List.of()),
                new PortFamilyDescriptor(radioactive, IOType.OUTPUT, 0, List.of())), List.of("ae2"));
        PortTagSet tags = PortTagSet.forKind(kind);
        assertTrue(tags.tags().containsAll(List.of(MMCR.id("mekanism_chemical_ports"),
                MMCR.id("mekanism_radioactive_chemical_ports"), MMCR.id("mekanism_radioactive_chemical_input_ports"),
                MMCR.id("mekanism_radioactive_chemical_output_ports"))));
        assertTrue(tags.optionalEntries());
    }

    private record TestPortKind(String id, IOType ioType, List<PortFamilyDescriptor> families,
                                List<String> modDependencies) implements IOPortKind {
        @Override
        public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
            return (position, state) -> null;
        }

        @Override
        public PortDefinition definition() {
            return PortDefinition.of(MMCR.id(id), List.of());
        }
    }
}
