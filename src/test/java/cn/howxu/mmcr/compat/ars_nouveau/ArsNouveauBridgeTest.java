package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies optional bridge absence without an Ars runtime or a game world.
 *
 * @author howxu <dev@howxu.cn>
 */
class ArsNouveauBridgeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void resetBridge() {
        ArsNouveauBridgeBootstrap.resetForTesting();
        SourceRequirement.installUnavailableHandler();
    }

    @Test
    void absentArsDoesNotContributePortsMenusOrCapabilities() {
        ArsNouveauBridge bridge = ArsNouveauBridgeBootstrap.selectForTesting(false);
        List<String> menus = new ArrayList<>();
        bridge.registerMenus((id, factory) -> menus.add(id));
        bridge.registerCapabilities(null);

        assertThat(bridge.available()).isFalse();
        assertThat(bridge.portKinds()).isEmpty();
        assertThat(menus).isEmpty();
        for (String id : List.of(ArsSourceIds.INPUT, ArsSourceIds.OUTPUT)) {
            assertThat(bridge.isSourcePort(id)).isFalse();
            assertThat(bridge.createMenu(id, 1, null, null, BlockPos.ZERO)).isNull();
        }
        assertThat(bridge.isDominionWand(ItemStack.EMPTY)).isFalse();
        assertThat(bridge.sourceIcon()).isSameAs(ItemStack.EMPTY);
        assertThatThrownBy(() -> bridge.createPort(BlockPos.ZERO, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Ars Nouveau is unavailable");
        assertThatThrownBy(() -> bridge.createCapability(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Ars Nouveau is unavailable");
    }

    @Test
    void absentSelectionWorksWithNativeAndLoadedClassesDenied() throws Exception {
        NativeDenyingClassLoader loader = new NativeDenyingClassLoader();
        Class<?> bootstrap = Class.forName(ArsNouveauBridgeBootstrap.class.getName(), true, loader);
        Class<?> contract = Class.forName(ArsNouveauBridge.class.getName(), true, loader);
        Object bridge = bootstrap.getMethod("selectForTesting", boolean.class).invoke(null, false);

        assertThat(contract.getMethod("available").invoke(bridge)).isEqualTo(false);
        assertThat((List<?>) contract.getMethod("portKinds").invoke(bridge)).isEmpty();
        assertThat(contract.getMethod("isSourcePort", String.class).invoke(bridge, ArsSourceIds.INPUT)).isEqualTo(false);
        assertThat(loader.denied).isEmpty();
    }

    @Test
    void bootstrapRestoresUnavailableHandlerWithoutReplacingCanonicalType() {
        SourceRequirement value = SourceRequirement.input(100L);
        var type = SourceRequirement.TYPE;
        var handler = type.handler();
        SourceRequirement.installHandler((requirement, capabilities, context) ->
                new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null));
        ArsNouveauBridgeBootstrap.installForTesting(ArsNouveauBridgeBootstrap.selectForTesting(false));

        ArsNouveauBridgeBootstrap.bootstrap();

        assertThat(value.type()).isSameAs(type);
        assertThat(type.handler()).isSameAs(handler);
        var plan = handler.plan(value, List.of(), new PlanningContext(2L, 0));
        assertThat(plan.successful()).isFalse();
        assertThat(plan.failure().reason()).isSameAs(SourceFailureReasons.ARS_UNAVAILABLE);
    }

    @Test
    void publicEntryPointsShareTheSelectedBridgeAndRejectNullOverrides() {
        ArsNouveauBridge selected = ArsNouveauBridgeBootstrap.selectForTesting(false);
        ArsNouveauBridgeBootstrap.installForTesting(selected);
        assertThat(ArsNouveauBridge.get()).isSameAs(selected);
        assertThat(ArsNouveauBridgeBootstrap.get()).isSameAs(selected);
        assertThatThrownBy(() -> ArsNouveauBridgeBootstrap.installForTesting(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(ArsNouveauBridge.get()).isSameAs(selected);
        ArsNouveauBridgeBootstrap.resetForTesting();
        assertThat(ArsNouveauBridge.get().available()).isFalse();
        assertThat(ArsNouveauBridge.get()).isSameAs(ArsNouveauBridgeBootstrap.get());
    }

    /**
     * Loads the neutral bridge in isolation and fails any attempted optional class link.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class NativeDenyingClassLoader extends ClassLoader {
        private final List<String> denied = new ArrayList<>();

        private NativeDenyingClassLoader() {
            super(ArsNouveauBridgeTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.hollingsworth.arsnouveau.")
                    || name.startsWith("cn.howxu.mmcr.compat.ars_nouveau.loaded.")) {
                denied.add(name);
                throw new ClassNotFoundException(name);
            }
            if (!name.equals(ArsNouveauBridge.class.getName())
                    && !name.equals(ArsNouveauBridgeBootstrap.class.getName())
                    && !name.equals(UnavailableArsNouveauBridge.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream source = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (source == null) throw new ClassNotFoundException(name);
                        byte[] bytes = source.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
