package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.heat.IHeatCapacitor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies worker-safe Mekanism heat planning and commit behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class HeatCapabilityTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void heat_capability_plans_temperature_checks_and_output_heat() throws Exception {
        AtomicReference<Double> heat = new AtomicReference<>(1_000D);
        IHeatCapacitor capacitor = capacitor(heat, 100D);
        HeatPortCapability capability = new HeatPortCapability(capacitor, IOType.OUTPUT);
        AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
        AsyncCapabilitySnapshot.Heat snapshot = (AsyncCapabilitySnapshot.Heat) invoke(facet,
                "captureSnapshotOnServerThread");
        AsyncCapabilityPlanner planner = (AsyncCapabilityPlanner) invoke(facet, "workerPlannerOnServerThread");

        assertThat(planner.plan(snapshot, new AsyncCapabilityRequest.Heat(capability.type().id(), 1L,
                9D, true, 9L))).isPresent();
        AsyncCapabilityOperation operation = planner.plan(snapshot, new AsyncCapabilityRequest.Heat(
                capability.type().id(), 1L, 50D, false, 50L)).orElseThrow();
        assertThat(invokeCommit(facet, operation)).isTrue();

        assertThat(heat.get()).isEqualTo(1_050D);
    }

    private static IHeatCapacitor capacitor(AtomicReference<Double> heat, double capacity) {
        return (IHeatCapacitor) Proxy.newProxyInstance(IHeatCapacitor.class.getClassLoader(),
                new Class<?>[]{IHeatCapacitor.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getHeat" -> heat.get();
                    case "getTemperature" -> heat.get() / capacity;
                    case "getHeatCapacity" -> capacity;
                    case "setHeat" -> {
                        heat.set((double) arguments[0]);
                        yield null;
                    }
                    case "handleHeat" -> {
                        heat.set(heat.get() + (double) arguments[0]);
                        yield null;
                    }
                    case "toString" -> "TestHeatCapacitor";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == int.class ? 0
                            : method.getReturnType() == long.class ? 0L
                            : method.getReturnType() == double.class ? 0D : null;
                });
    }

    private static Object invoke(AsyncPlanningFacet facet, String name) throws Exception {
        Method method = AsyncPlanningFacet.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(facet);
    }

    private static boolean invokeCommit(AsyncPlanningFacet facet, AsyncCapabilityOperation operation) throws Exception {
        Method method = AsyncPlanningFacet.class.getDeclaredMethod("commitNativeOnServerThread",
                AsyncCapabilityOperation.class);
        method.setAccessible(true);
        return ((cn.howxu.mmcr.api.capability.plan.CapabilityResult) method.invoke(facet, operation)).success();
    }
}
