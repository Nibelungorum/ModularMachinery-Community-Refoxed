package cn.howxu.mmcr.test;

import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortMenu;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** Cold-loader fixture: controller menus precede loaded chemical and heat menu use.
 * @author howxu <dev@howxu.cn>
 */
public final class TestBootstrapMenuFixture {
    private TestBootstrapMenuFixture() { }

    public static void verify(int initialOverride) throws Exception {
        Field override = MekanismBridgeBootstrap.class.getDeclaredField("testingBridge");
        override.setAccessible(true);
        MekanismBridge original = initialOverride == 0 ? null
                : MekanismBridgeBootstrap.selectForTesting(initialOverride == 1);
        if (original != null) MekanismBridgeBootstrap.installForTesting(original);

        TestBootstrap.bootstrap();

        assertThat(override.get(null)).isSameAs(original);
        if (original != null) assertThat(MekanismBridge.get()).isSameAs(original);
        assertThat(PortKinds.all()).filteredOn(kind -> kind.modDependencies().contains("mekanism"))
                .extracting(IOPortKind::id).containsExactlyInAnyOrderElementsOf(MekanismBridge.get().portDeclarations()
                        .stream().map(MekanismBridge.PortDeclaration::id).toList());
        bind(ModUIs.MACHINE_CONTROLLER, new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
        bind(ModUIs.FACTORY_CONTROLLER, new MenuType<>(FactoryControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
        Inventory controllerInventory = new Inventory(null, null);
        assertThat(new MachineControllerMenu(1, controllerInventory).uiOpenData()).isNotNull();
        assertThat(FactoryControllerMenu.clientOpen(2, controllerInventory).uiOpenData()).isNotNull();

        MekanismBridge loaded = MekanismBridgeBootstrap.selectForTesting(true);
        MekanismBridgeBootstrap.installForTesting(loaded);
        bind(ModUIs.CHEMICAL_PORT, new MenuType<>((id, inventory) ->
                new ChemicalPortMenu(id, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
        bind(ModUIs.HEAT_PORT, new MenuType<>((id, inventory) ->
                new HeatPortMenu(id, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        ServerPlayer player = (ServerPlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(ServerPlayer.class);
        Inventory inventory = new Inventory(player, null);
        ChemicalPortMenu chemical = new ChemicalPortMenu(3, inventory, BlockPos.ZERO);
        HeatPortMenu heat = new HeatPortMenu(4, inventory, BlockPos.ZERO);
        assertThat(MekanismBridge.get().capabilityIdForMenu(chemical)).isEqualTo(MekanismRecipeTypes.CHEMICAL);
        assertThat(MekanismBridge.get().capabilityIdForMenu(heat)).isEqualTo(MekanismRecipeTypes.HEAT);
        assertThat(MekanismBridge.get().isPortMenuAt(chemical, BlockPos.ZERO, null)).isTrue();
        assertThat(MekanismBridge.get().isPortMenuAt(heat, BlockPos.ZERO, null)).isTrue();

        MekanismBridge unavailable = MekanismBridgeBootstrap.selectForTesting(false);
        MekanismBridgeBootstrap.installForTesting(unavailable);
        TestBootstrap.bootstrap();
        assertThat(MekanismBridge.get()).isSameAs(unavailable);
        assertThat(MekanismBridge.get().capabilityIdForMenu(chemical)).isNull();
        assertThat(MekanismBridge.get().isPortMenuAt(heat, BlockPos.ZERO, null)).isFalse();
        MekanismBridgeBootstrap.installForTesting(loaded);
        TestBootstrap.bootstrap();
        assertThat(MekanismBridge.get()).isSameAs(loaded);
        assertThat(MekanismBridge.get().capabilityIdForMenu(chemical)).isEqualTo(MekanismRecipeTypes.CHEMICAL);
    }

    private static void bind(Object holder, MenuType<?> value) throws Exception {
        Class<?> type = holder.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) throw new NoSuchFieldException("holder");
        field.setAccessible(true);
        field.set(holder, Holder.direct(value));
    }
}
