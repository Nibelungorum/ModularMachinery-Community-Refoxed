package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Optional class linking, canonical registration and real absent-mod planning.
 * @author howxu <dev@howxu.cn>
 */
class PneumaticCraftBridgeTest {
    @AfterEach
    void resetBridge() {
        PneumaticCraftBridgeBootstrap.resetForTesting();
    }

    @Test
    void absentDependencyDecodesCanonicalAirButNeverProducesOperations() {
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            PneumaticCraftBridgeBootstrap.installForTesting(PneumaticCraftBridgeBootstrap.selectForTesting(false));
            PneumaticRecipeTypes.register();
            var requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"type\":\"pneumaticcraft:air\",\"io\":\"input\",\"air_per_tick\":0,\"min_pressure\":4}"))
                    .getOrThrow();
            assertThat(requirement).isEqualTo(AirRequirement.input(0, 4F));
            var bridge = PneumaticCraftBridge.get();
            assertThat(bridge.available()).isFalse();
            assertThat(bridge.portKinds()).isEmpty();
            bridge.registerPorts(null);
            var result = new RequirementPlanner().plan(List.of(requirement), List.of(), new PlanningContext(1, 0));
            assertThat(result.plan()).isNull();
            assertThat(result.failure().reason()).isSameAs(AirFailureReasons.UNAVAILABLE);
        }
    }

    @Test
    void registrationIsIdempotentRejectsConflictsAndWorksAfterScopeClose() {
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            PneumaticRecipeTypes.register();
            PneumaticRecipeTypes.register();
            assertThat(RequirementHandlerRegistry.typeFor(PneumaticIds.AIR)).isSameAs(AirRequirement.TYPE);
        }
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            PneumaticRecipeTypes.register();
            assertThat(RequirementHandlerRegistry.typeFor(PneumaticIds.AIR)).isSameAs(AirRequirement.TYPE);
        }
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(new RequirementType.Definition<>(PneumaticIds.AIR,
                    AirRequirement.CODEC, new AirRequirementHandler()));
            assertThatThrownBy(PneumaticRecipeTypes::register).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testOverrideRejectsNullAndResetRestoresActualModSelection() {
        PneumaticCraftBridge override = () -> true;
        PneumaticCraftBridgeBootstrap.installForTesting(override);
        assertThat(PneumaticCraftBridge.get()).isSameAs(override);
        assertThatThrownBy(() -> PneumaticCraftBridgeBootstrap.installForTesting(null)).isInstanceOf(NullPointerException.class);
        assertThat(PneumaticCraftBridge.get()).isSameAs(override);
        PneumaticCraftBridgeBootstrap.resetForTesting();
        ModList mods = ModList.get();
        assertThat(PneumaticCraftBridge.get().available()).isEqualTo(mods != null && mods.isLoaded(PneumaticIds.MOD_ID));
    }

    @Test
    void neutralClassesAndAbsentCodecPlannerLoadWithoutAnyNativeOrLoadedLink() throws Exception {
        var loader = new NativeDenyingClassLoader();
        Class<?> bootstrap = Class.forName(PneumaticCraftBridgeBootstrap.class.getName(), true, loader);
        Class<?> bridgeContract = Class.forName(PneumaticCraftBridge.class.getName(), true, loader);
        Object bridge = bootstrap.getMethod("selectForTesting", boolean.class).invoke(null, false);
        assertThat(bridgeContract.getMethod("available").invoke(bridge)).isEqualTo(false);
        assertThat((List<?>) bridgeContract.getMethod("portKinds").invoke(bridge)).isEmpty();
        bootstrap.getMethod("installForTesting", bridgeContract).invoke(null, bridge);
        for (String name : List.of(AirRequirement.class.getName(), AirRequirementHandler.class.getName(),
                AirFailureReasons.class.getName(), PneumaticRecipeTypes.class.getName(),
                "cn.howxu.mmcr.api.compat.pneumaticcraft.AirState",
                "cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet")) {
            assertThat(Class.forName(name, true, loader).getClassLoader()).isSameAs(loader);
        }
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            Class.forName(PneumaticRecipeTypes.class.getName(), true, loader).getMethod("register").invoke(null);
            MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"type\":\"pneumaticcraft:air\",\"io\":\"input\",\"air_per_tick\":1}"))
                    .getOrThrow();
            assertThat(requirement.getClass().getClassLoader()).isSameAs(loader);
            var result = new RequirementPlanner().plan(List.of(requirement), List.of(), new PlanningContext(1, 0));
            assertThat(result.plan()).isNull();
            assertThat(result.failure().reason().id()).isEqualTo(AirFailureReasons.UNAVAILABLE.id());
        }
        assertThat(loader.denied).isEmpty();
    }

    @Test
    void installedModLinkageErrorsAreNotSilentlyConvertedToUnavailable() throws Exception {
        var loader = new NativeDenyingClassLoader();
        loader.failLoadedWithLinkageError = true;
        Class<?> bootstrap = Class.forName(PneumaticCraftBridgeBootstrap.class.getName(), true, loader);
        assertThatThrownBy(() -> bootstrap.getMethod("selectForTesting", boolean.class).invoke(null, true))
                .hasCauseInstanceOf(NoClassDefFoundError.class);
    }

    /** Child-first neutral packages; native packages are forbidden and attempted links recorded.
     * @author howxu <dev@howxu.cn>
     */
    private static final class NativeDenyingClassLoader extends ClassLoader {
        private final List<String> denied = new ArrayList<>();
        private boolean failLoadedWithLinkageError;

        private NativeDenyingClassLoader() {
            super(PneumaticCraftBridgeTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("me.desht.pneumaticcraft.")
                    || name.startsWith("cn.howxu.mmcr.compat.pneumaticcraft.loaded.")) {
                denied.add(name);
                if (failLoadedWithLinkageError) throw new NoClassDefFoundError(name);
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith("cn.howxu.mmcr.compat.pneumaticcraft.")
                    && !name.startsWith("cn.howxu.mmcr.api.compat.pneumaticcraft.")) {
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
