package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.MekanismPortSizes;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.AutomationType;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import mekanism.api.heat.HeatAPI;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies loaded Mekanism port storage restrictions and defaults.
 *
 * @author howxu <dev@howxu.cn>
 */
class LoadedPortStorageTest {
    private static final BlockPos POS = new BlockPos(2, 3, 4);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void normal_port_rejects_radioactive_chemical() {
        IChemicalTank tank = LoadedPortStorage.normalChemicalTank(64_000L, listener());

        assertThat(insert(tank, chemical("radioactive", true), 1_000)).isZero();
    }

    @Test
    void normal_port_accepts_non_radioactive_chemical() {
        IChemicalTank tank = LoadedPortStorage.normalChemicalTank(64_000L, listener());

        assertThat(insert(tank, chemical("oxygen", false), 1_000)).isEqualTo(1_000);
    }

    @Test
    void chemical_port_sizes_limit_real_insertions() {
        for (long capacity : new long[]{
                MekanismPortSizes.CHEMICAL_BASIC_CAPACITY,
                MekanismPortSizes.CHEMICAL_ADVANCED_CAPACITY,
                MekanismPortSizes.CHEMICAL_ELITE_CAPACITY,
                MekanismPortSizes.CHEMICAL_ULTIMATE_CAPACITY}) {
            IChemicalTank tank = LoadedPortStorage.normalChemicalTank(capacity, listener());

            assertThat(insert(tank, chemical("capacity_" + capacity, false), (int) capacity + 1))
                    .isEqualTo(capacity);
        }
    }

    @Test
    void radioactive_port_rejects_non_radioactive_chemical() {
        IChemicalTank tank = LoadedPortStorage.radioactiveChemicalTank(512_000L, listener());

        assertThat(insert(tank, chemical("oxygen", false), 1_000)).isZero();
    }

    @Test
    void radioactive_port_accepts_radioactive_chemical() {
        IChemicalTank tank = LoadedPortStorage.radioactiveChemicalTank(512_000L, listener());

        assertThat(insert(tank, chemical("radioactive_accepted", true), 1_000)).isEqualTo(1_000);
    }

    @Test
    void chemical_tank_round_trips_long_amount_through_compound_tag() {
        long amount = (long) Integer.MAX_VALUE + 1L;
        ChemicalStack chemical = chemical("persisted_long_amount", false);
        IChemicalTank source = LoadedPortStorage.normalChemicalTank(amount, listener());
        source.setStack(chemical.copyWithAmount(amount));
        HolderLookup.Provider registries = HolderLookup.Provider.create(
                Stream.of(MekanismAPI.CHEMICAL_REGISTRY.asLookup()));

        CompoundTag saved = source.serializeNBT(registries);
        IChemicalTank restored = LoadedPortStorage.normalChemicalTank(amount, listener());
        restored.deserializeNBT(registries, saved);

        assertThat(ChemicalStack.isSameChemical(restored.getStack(), chemical)).isTrue();
        assertThat(restored.getStored()).isEqualTo(amount);
    }

    @Test
    void heat_port_uses_fixed_capacity_and_local_ambient_temperature() {
        BasicHeatCapacitor capacitor = LoadedPortStorage.heatCapacitor(null, POS, listener());

        assertThat(capacitor.getHeatCapacity()).isEqualTo(300D);
        assertThat(capacitor.getTemperature()).isEqualTo(HeatAPI.getAmbientTemp(null, POS));

        capacitor.handleHeat(300D);
        capacitor.update();

        assertThat(capacitor.getTemperature()).isEqualTo(HeatAPI.getAmbientTemp(null, POS) + 1D);
    }

    @Test
    void loaded_capabilities_expose_the_required_facets_and_directions() {
        ChemicalPortCapability chemicalCapability = new ChemicalPortCapability(
                LoadedPortStorage.normalChemicalTank(64_000L, listener()), IOType.INPUT);
        HeatPortCapability heatCapability = new HeatPortCapability(
                LoadedPortStorage.heatCapacitor(null, POS, listener()), IOType.OUTPUT);

        assertThat(chemicalCapability.view().facets())
                .contains(TransferFacet.class, OperationFacet.class,
                        PresentationFacet.class, SyncFacet.class);
        assertThat(heatCapability.view().facets())
                .contains(OperationFacet.class, PresentationFacet.class, SyncFacet.class)
                .doesNotContain(TransferFacet.class);
        assertThat(chemicalCapability.directions()).isEqualTo(
                CapabilityDirections.input());
        assertThat(heatCapability.directions()).isEqualTo(
                CapabilityDirections.output());
    }

    private static long insert(IChemicalTank tank, ChemicalStack stack, long amount) {
        ChemicalStack requested = stack.copyWithAmount(amount);
        return amount - tank.insert(requested, Action.EXECUTE, AutomationType.EXTERNAL).getAmount();
    }

    private static ChemicalStack chemical(String path, boolean radioactive) {
        return new ChemicalStack(registerChemical(path, radioactive), 1L);
    }

    private static Holder.Reference<Chemical> registerChemical(String path, boolean radioactive) {
        ResourceKey<Chemical> key = ResourceKey.create(
                MekanismAPI.CHEMICAL_REGISTRY_NAME,
                ResourceLocation.fromNamespaceAndPath("mmcr_test", path));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        if (registry.get(key) != null) return registry.getHolder(key).orElseThrow();
        registry.unfreeze();
        if (registry.get(MekanismAPI.EMPTY_CHEMICAL_KEY) == null) {
            Registry.registerForHolder(registry, MekanismAPI.EMPTY_CHEMICAL_KEY, MekanismAPI.EMPTY_CHEMICAL);
        }
        Chemical value = new Chemical(ChemicalBuilder.builder()) {
            @Override
            public boolean isRadioactive() {
                return radioactive;
            }
        };
        Registry.registerForHolder(registry, key, value);
        registry.freeze();
        return registry.getHolder(key).orElseThrow();
    }

    private static IContentsListener listener() {
        return () -> {
        };
    }

    private static final class LoadedPortStorage {
        private static IChemicalTank normalChemicalTank(long capacity, IContentsListener listener) {
            return BasicChemicalTank.createModern(capacity, (stack, automation) -> true,
                    (stack, automation) -> true, stack -> !stack.isRadioactive(),
                    ChemicalAttributeValidator.ALWAYS_ALLOW, listener);
        }

        private static IChemicalTank radioactiveChemicalTank(long capacity, IContentsListener listener) {
            return BasicChemicalTank.createModern(capacity, (stack, automation) -> true,
                    (stack, automation) -> true, ChemicalStack::isRadioactive,
                    ChemicalAttributeValidator.ALWAYS_ALLOW, listener);
        }

        private static BasicHeatCapacitor heatCapacitor(
                net.minecraft.world.level.Level level, BlockPos pos, IContentsListener listener) {
            return HeatPortBlockEntity.heatCapacitor(level, pos, listener);
        }
    }
}
