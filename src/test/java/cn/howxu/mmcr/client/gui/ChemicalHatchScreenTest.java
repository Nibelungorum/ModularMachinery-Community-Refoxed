package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.client.render.ChemicalGuiRenderer;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortMenu;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortMenu;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the pure chemical display state used by the hatch screen.
 *
 * @author howxu <dev@howxu.cn>
 */
class ChemicalHatchScreenTest {
    private static final int EXPECTED_TINT = 0xFF66CCFF;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        MekanismBridgeBootstrap.installForTesting(new LoadedMekanismBridge());
        TestBootstrap.bootstrap();
        bind(ModUIs.CHEMICAL_PORT, new MenuType<>((containerId, inventory) ->
                new ChemicalPortMenu(containerId, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
        bind(ModUIs.HEAT_PORT, new MenuType<>((containerId, inventory) ->
                new HeatPortMenu(containerId, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
    }

    @AfterAll
    static void resetBridge() {
        MekanismBridgeBootstrap.resetForTesting();
    }

    @Test
    void chemical_render_state_uses_the_loaded_menu_resource_tint_and_fill_inputs() {
        ChemicalPortMenu menu = filledChemicalMenu();
        ChemicalGuiRenderer.ChemicalRenderState state = ChemicalHatchScreen.renderState(menu, 61);

        assertThat(menu.chemicalAmount()).isEqualTo(500L);
        assertThat(menu.chemicalCapacity()).isEqualTo(64_000L);
        assertThat(menu.chemicalTint()).isEqualTo(EXPECTED_TINT);
        assertThat(state.fillHeight()).isEqualTo(1);
        assertThat(state.fillHeight()).isEqualTo(
                cn.howxu.mmcr.client.render.FluidGuiRenderer.fillHeight(
                        menu.chemicalAmount(), menu.chemicalCapacity(), 61));
        assertThat(state.tint()).isEqualTo(EXPECTED_TINT).isNotEqualTo(0xFFFFFFFF);
        assertThat(state.identifier()).isEqualTo(menu.chemicalIdentifier());
    }

    @Test
    void chemical_render_state_is_empty_when_the_menu_has_no_loaded_chemical() throws Exception {
        ChemicalPortMenu menu = new ChemicalPortMenu(1, testInventory(), chemicalPort());
        ChemicalGuiRenderer.ChemicalRenderState state = ChemicalHatchScreen.renderState(menu, 61);

        assertThat(state.fillHeight()).isZero();
        assertThat(state.identifier()).isNull();
    }

    private static ChemicalPortMenu filledChemicalMenu() {
        ChemicalPortBlockEntity port = chemicalPort();
        ChemicalResource resource = ChemicalResource.of(registerChemical("gui_oxygen"));
        try (Transaction transaction = Transaction.openRoot()) {
            port.chemicalTank().insert(resource, 500, transaction, AutomationType.EXTERNAL);
            transaction.commit();
        }
        try {
            return new ChemicalPortMenu(1, testInventory(), port);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ChemicalPortBlockEntity chemicalPort() {
        return new ChemicalProbePort(BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
    }

    private static Holder.Reference<Chemical> registerChemical(String path) {
        ResourceKey<Chemical> key = ResourceKey.create(
                MekanismAPI.CHEMICAL_REGISTRY_NAME, Identifier.fromNamespaceAndPath("mmcr_test", path));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        return registry.get(key).orElseGet(() -> {
            registry.unfreeze(true);
            Chemical value = new Chemical(ChemicalBuilder.builder().tint(EXPECTED_TINT));
            Registry.register(registry, key.identifier(), value);
            registry.freeze();
            return registry.get(key).orElseThrow();
        });
    }

    @Test
    void displayed_capability_is_not_an_auto_io_permission() throws Exception {
        var menu = new ChemicalPortMenu(1, testInventory(), chemicalPort());
        assertThat(menu.displayedCapability()).isEqualTo(MekanismRecipeTypes.CHEMICAL);
        assertThat(menu.owner().configurableAutoIOIds()).isEmpty();
        var heat = new HeatPortMenu(1, testInventory(), BlockPos.ZERO);
        assertThat(heat.displayedCapability()).isEqualTo(MekanismRecipeTypes.HEAT);
        assertThat(((HeatHatchScreen) unsafe().allocateInstance(HeatHatchScreen.class)).supportsAutoIOControlPage()).isFalse();
    }

    private static Inventory testInventory() throws Exception {
        Player player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        return new Inventory(player, null);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }

    private static final class ChemicalProbePort extends ChemicalPortBlockEntity {
        private ChemicalProbePort(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos, state,
                    PortKinds.ITEM_INPUT, 64_000L, false);
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }

        @Override
        public IOPortKind kind() {
            return PortKinds.ITEM_INPUT;
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of());
        }
    }
}
