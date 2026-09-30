package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.menu.ISubMenu;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.compat.extendedae.loaded.LoadedExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedInputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.LoadedAE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.jade.AE2JadeRegistration;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.AsyncOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.OutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.StockingInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.test.TestBootstrap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.IWailaCommonRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the optional ExtendedAE contribution boundary in the AE2 bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
class ExtendedAEBridgeTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        AE2TestFixtures.ensureAE2KeyTypesInitialized();
        AE2TestFixtures.bindAE2InterfaceItem();
        bindTestEntityType(InputInterfaceKind.INSTANCE);
        bindTestEntityType(StockingInterfaceKind.INSTANCE);
        bindTestEntityType(OutputInterfaceKind.INSTANCE);
        bindTestEntityType(AsyncOutputInterfaceKind.INSTANCE);
        bindTestEntityType(PatternInterfaceKind.INSTANCE);
        bindTestEntityType(ExtendedInputInterfaceKind.INSTANCE);
    }

    @AfterEach
    void resetContributor() {
        ExtendedAEContributorBootstrap.resetForTesting();
    }

    @Test
    void unavailableExtendedAeContributorAddsNoPorts() {
        ExtendedAEContributor contributor = ExtendedAEContributorBootstrap.selectForTesting(false);

        assertThat(contributor.available()).isFalse();
        assertThat(contributor.portKinds()).isEmpty();
        assertThat(contributor.isPort("eae_me_extended_input_interface")).isFalse();
    }

    @Test
    void loadedExtendedAeContributorProvidesAllExtendedInterfaceKinds() {
        ExtendedAEContributor contributor = new LoadedExtendedAEContributor();

        assertThat(contributor.available()).isTrue();
        assertThat(contributor.portKinds()).extracting(IOPortKind::id)
                .containsExactlyInAnyOrder(
                        "eae_me_extended_input_interface",
                        "eae_me_extended_stocking_input_interface",
                        "eae_me_extended_output_interface",
                        "eae_me_oversize_input_interface",
                        "eae_me_oversize_output_interface",
                        "eae_me_extended_pattern_interface");
        assertThat(contributor.portKinds())
                .allSatisfy(kind -> assertThat(kind.modDependencies()).containsExactly("ae2", "extendedae"));
    }

    @Test
    void unavailableContributorDoesNotChangeAe2BridgeBehavior() {
        ExtendedAEContributorBootstrap.installForTesting(new UnavailableTestContributor());
        LoadedAE2Bridge bridge = new LoadedAE2Bridge();

        assertThat(bridge.portKinds()).extracting(IOPortKind::id).containsExactly(
                "ae2_me_input_interface",
                "ae2_me_stocking_input_interface",
                "ae2_me_output_interface",
                "ae2_me_async_output_interface",
                "ae2_me_pattern_interface");
        assertThat(bridge.isPort("ae2_me_input_interface")).isTrue();
        assertThat(bridge.isPort("eae_me_extended_input_interface")).isFalse();
        assertThat(bridge.openMenu(null, LevelStub.create(Blocks.AIR, 1, 1, 1, BlockPos.ZERO), BlockPos.ZERO))
                .isFalse();
        assertThatCode(() -> bridge.registerCapabilities(capabilityEvent())).doesNotThrowAnyException();

        List<Registration> registrations = new ArrayList<>();
        AE2JadeRegistration.registerCommon(commonRegistration(registrations));
        assertThat(registrations).hasSize(5);
    }

    @Test
    void availableContributorDoesNotOverrideNativeAe2Paths() {
        ExtendedAEContributorBootstrap.installForTesting(new AvailableTestContributor());
        LoadedAE2Bridge bridge = new LoadedAE2Bridge();

        assertThat(bridge.isPort("ae2_me_input_interface")).isTrue();
        var host = InputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
        Level level = LevelStub.createWithBlockEntities(List.of(host));
        host.setLevel(level);
        assertThatThrownBy(() -> bridge.openMenu(null, level, BlockPos.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Menus must be opened on the server.");
    }

    @Test
    void availableContributorRoutesExtendedAeHostsBeforeSharedNativeEntityTypes() {
        MenuRoutingTestContributor contributor = new MenuRoutingTestContributor();
        ExtendedAEContributorBootstrap.installForTesting(contributor);
        LoadedAE2Bridge bridge = new LoadedAE2Bridge();
        var extendedHost = ExtendedInputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
        Level extendedLevel = LevelStub.createWithBlockEntities(List.of(extendedHost));
        extendedHost.setLevel(extendedLevel);

        assertThat(bridge.openMenu(null, extendedLevel, BlockPos.ZERO)).isTrue();
        assertThat(contributor.extendedMenuOpened).isTrue();

        var nativeHost = InputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
        Level nativeLevel = LevelStub.createWithBlockEntities(List.of(nativeHost));
        nativeHost.setLevel(nativeLevel);

        assertThatThrownBy(() -> bridge.openMenu(null, nativeLevel, BlockPos.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Menus must be opened on the server.");
        assertThat(contributor.nativeMenuChecked).isTrue();
    }

    private static RegisterCapabilitiesEvent capabilityEvent() {
        try {
            Constructor<RegisterCapabilitiesEvent> constructor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create capability registration event", exception);
        }
    }

    private static IWailaCommonRegistration commonRegistration(List<Registration> registrations) {
        return (IWailaCommonRegistration) Proxy.newProxyInstance(
                IWailaCommonRegistration.class.getClassLoader(),
                new Class<?>[]{IWailaCommonRegistration.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("registerBlockDataProvider")) {
                        registrations.add(new Registration(args[0], (Class<?>) args[1]));
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static void bindTestEntityType(IOPortKind kind) {
        AE2TestFixtures.bindEntityType(kind.id(), kind.entityFactory());
    }

    private static void bindTestAE2InterfaceItem() {
        AE2TestFixtures.bindAE2InterfaceItem();
    }

    /**
     * Captures a real Jade registration emitted by the bridge.
     *
     * @author howxu <dev@howxu.cn>
     */
    private record Registration(Object provider, Class<?> blockEntityType) {
    }

    /**
     * Fails if a bridge delegates work after declaring ExtendedAE unavailable.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class UnavailableTestContributor implements ExtendedAEContributor {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public List<IOPortKind> portKinds() {
            throw unexpectedDelegation();
        }

        @Override
        public boolean isPort(String id) {
            throw unexpectedDelegation();
        }

        @Override
        public boolean openMenu(ServerPlayer player, Level level, BlockPos pos) {
            throw unexpectedDelegation();
        }

        @Override
        public boolean returnToMainMenu(ServerPlayer player, ISubMenu subMenu, IOPortKind kind) {
            throw unexpectedDelegation();
        }

        @Override
        public @Nullable ItemStack mainMenuIcon(IOPortKind kind) {
            throw unexpectedDelegation();
        }

        @Override
        public void registerCapabilities(RegisterCapabilitiesEvent event) {
            throw unexpectedDelegation();
        }

        private AssertionError unexpectedDelegation() {
            return new AssertionError("Unavailable ExtendedAE contributor must not be delegated to");
        }
    }

    /**
     * Fails if a native AE2 path is delegated to an available ExtendedAE contributor.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class AvailableTestContributor implements ExtendedAEContributor {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<IOPortKind> portKinds() {
            return List.of();
        }

        @Override
        public boolean isPort(String id) {
            throw unexpectedDelegation();
        }

        @Override
        public boolean openMenu(ServerPlayer player, Level level, BlockPos pos) {
            return false;
        }

        @Override
        public boolean returnToMainMenu(ServerPlayer player, ISubMenu subMenu, IOPortKind kind) {
            throw unexpectedDelegation();
        }

        @Override
        public @Nullable ItemStack mainMenuIcon(IOPortKind kind) {
            throw unexpectedDelegation();
        }

        @Override
        public void registerCapabilities(RegisterCapabilitiesEvent event) {
        }

        private AssertionError unexpectedDelegation() {
            return new AssertionError("Native AE2 paths must not be delegated beyond menu fallback");
        }
    }

    /** Records the contributor-first menu routing contract. */
    private static final class MenuRoutingTestContributor implements ExtendedAEContributor {
        private boolean extendedMenuOpened;
        private boolean nativeMenuChecked;

        @Override public boolean available() { return true; }
        @Override public List<IOPortKind> portKinds() { return List.of(ExtendedInputInterfaceKind.INSTANCE); }
        @Override public boolean isPort(String id) { return ExtendedInputInterfaceKind.INSTANCE.id().equals(id); }

        @Override
        public boolean openMenu(ServerPlayer player, Level level, BlockPos pos) {
            if (level.getBlockEntity(pos) instanceof InputInterfaceBlockEntity host
                    && host.kind() == ExtendedInputInterfaceKind.INSTANCE) {
                extendedMenuOpened = true;
                return true;
            }
            nativeMenuChecked = true;
            return false;
        }

        @Override public boolean returnToMainMenu(ServerPlayer player, ISubMenu subMenu, IOPortKind kind) { return false; }
        @Override public @Nullable ItemStack mainMenuIcon(IOPortKind kind) { return null; }
        @Override public void registerCapabilities(RegisterCapabilitiesEvent event) {}
    }
}
