package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies absent integration and test override isolation. @author howxu <dev@howxu.cn> */
class BotaniaBridgeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @AfterEach
    void reset() {
        BotaniaBridgeBootstrap.resetForTesting();
        ManaRequirement.installUnavailableHandler();
    }

    @Test
    void absentBridgeIsInertAndRejectsNativeConstruction() {
        BotaniaBridge bridge = BotaniaBridgeBootstrap.selectForTesting(false);
        assertFalse(bridge.available());
        assertTrue(bridge.portKinds().isEmpty());
        assertDoesNotThrow(() -> bridge.registerCapabilities(null));
        assertDoesNotThrow(() -> bridge.tick(null));
        assertThrows(IllegalStateException.class, () -> bridge.createPort(null, null, null));
        assertThrows(IllegalStateException.class, () -> bridge.createBlock(null, null, null));
        assertThrows(IllegalStateException.class, () -> bridge.createCapability(null));
    }

    @Test
    void installedBridgeRemainsStableAcrossReads() {
        BotaniaBridge bridge = BotaniaBridgeBootstrap.selectForTesting(false);
        BotaniaBridgeBootstrap.installForTesting(bridge);
        assertSame(bridge, BotaniaBridge.get());
        assertSame(bridge, BotaniaBridge.get());
        assertThrows(NullPointerException.class, () -> BotaniaBridgeBootstrap.installForTesting(null));
    }

    @Test
    void bootstrapRestoresAbsentHandlerWithoutReplacingTheCanonicalType() {
        var type = ManaRequirement.TYPE;
        var handler = type.handler();
        ManaRequirement.installHandler((requirement, capabilities, context) ->
                new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null));
        BotaniaBridgeBootstrap.installForTesting(BotaniaBridgeBootstrap.selectForTesting(false));
        BotaniaBridgeBootstrap.bootstrap();
        assertSame(type, ManaRequirement.TYPE);
        assertSame(handler, type.handler());
        var plan = handler.plan(ManaRequirement.input(37), List.of(), new PlanningContext(2, 0));
        assertFalse(plan.successful());
        assertSame(ManaFailureReasons.BOTANIA_UNAVAILABLE, plan.failure().reason());
    }
}
