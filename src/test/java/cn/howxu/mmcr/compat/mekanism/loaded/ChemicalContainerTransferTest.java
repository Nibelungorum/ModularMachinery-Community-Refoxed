package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.test.TestBootstrap;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.api.MekanismAPI;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Native multi-tank container selection keeps incompatible chemicals and partial remainders.
 * @author howxu <dev@howxu.cn> */
class ChemicalContainerTransferTest {
    private static Holder<Chemical> firstChemical;
    private static Holder<Chemical> secondChemical;

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        registry.unfreeze();
        if (registry.get(MekanismAPI.EMPTY_CHEMICAL_KEY) == null) {
            Registry.registerForHolder(registry, MekanismAPI.EMPTY_CHEMICAL_KEY, MekanismAPI.EMPTY_CHEMICAL);
        }
        firstChemical = Registry.registerForHolder(registry, ResourceLocation.parse("mmcr_test:container_first"),
                new Chemical(ChemicalBuilder.builder()));
        secondChemical = Registry.registerForHolder(registry, ResourceLocation.parse("mmcr_test:container_second"),
                new Chemical(ChemicalBuilder.builder()));
        registry.freeze();
    }

    @Test
    void input_skips_incompatible_first_tank_and_preserves_unaccepted_remainder() {
        IChemicalTank first = BasicChemicalTank.createAllValid(4_000, () -> {});
        IChemicalTank second = BasicChemicalTank.createAllValid(4_000, () -> {});
        IChemicalTank port = BasicChemicalTank.createAllValid(4_000, () -> {});
        first.setStack(new ChemicalStack(firstChemical, 1_000));
        second.setStack(new ChemicalStack(secondChemical, 2_000));
        port.setStack(new ChemicalStack(secondChemical, 3_000));
        IMekanismChemicalHandler container = new IMekanismChemicalHandler() {
            public List<IChemicalTank> getChemicalTanks(Direction side) { return List.of(first, second); }
            public void onContentsChanged() {}
        };
        ChemicalStack drained = LoadedMekanismBridge.drainChemicalContainer(container, port);
        assertThat(drained.getAmount()).isEqualTo(1_000);
        assertThat(ChemicalStack.isSameChemical(drained, new ChemicalStack(secondChemical, 1))).isTrue();
        assertThat(first.getStored()).isEqualTo(1_000);
        assertThat(second.getStored()).isEqualTo(1_000);
        assertThat(port.getStored()).isEqualTo(3_000);
    }

    @Test
    void input_collects_the_same_chemical_from_all_compatible_tanks() {
        IChemicalTank first = BasicChemicalTank.createAllValid(4_000, () -> {});
        IChemicalTank second = BasicChemicalTank.createAllValid(4_000, () -> {});
        IChemicalTank port = BasicChemicalTank.createAllValid(4_000, () -> {});
        first.setStack(new ChemicalStack(firstChemical, 1_000));
        second.setStack(new ChemicalStack(firstChemical, 2_000));
        IMekanismChemicalHandler container = new IMekanismChemicalHandler() {
            public List<IChemicalTank> getChemicalTanks(Direction side) { return List.of(first, second); }
            public void onContentsChanged() {}
        };
        ChemicalStack drained = LoadedMekanismBridge.drainChemicalContainer(container, port);
        assertThat(drained.getAmount()).isEqualTo(3_000);
        assertThat(first.isEmpty()).isTrue();
        assertThat(second.isEmpty()).isTrue();
        assertThat(port.isEmpty()).isTrue();
    }
}
