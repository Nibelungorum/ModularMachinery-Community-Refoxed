package cn.howxu.mmcr.api.capability.status;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies stable failure reason registration and lookup.
 *
 * @author howxu <dev@howxu.cn>
 */
class FailureReasonRegistryTest {
    @BeforeEach
    void clear_registry_before_test() {
        FailureReasonRegistry.clearForTesting();
    }

    @AfterEach
    void clear_registry_after_test() {
        FailureReasonRegistry.clearForTesting();
    }

    @Test
    void duplicate_failure_reason_ids_are_rejected() {
        FailureReason reason = new FailureReason(ResourceLocation.fromNamespaceAndPath("mmcr_test", "duplicate_reason"),
                "gui.mmcr.failure.duplicate_reason", 10);
        FailureReasonRegistry.register(reason);
        assertThrows(IllegalArgumentException.class, () -> FailureReasonRegistry.register(reason));
        assertEquals(reason, FailureReasonRegistry.find(reason.id()));
    }

    @Test
    void frozen_registry_rejects_new_registrations() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "frozen_reason");
        FailureReasonRegistry.register(new FailureReason(id, "gui.mmcr.failure.frozen_reason", 10));

        FailureReasonRegistry.freeze();

        assertEquals(true, FailureReasonRegistry.isFrozen());
        assertThrows(IllegalStateException.class,
                () -> FailureReasonRegistry.register(new FailureReason(
                        ResourceLocation.fromNamespaceAndPath("mmcr_test", "after_freeze"),
                        "gui.mmcr.failure.after_freeze", 10)));
    }

    @Test
    void execution_status_exposes_a_registered_typed_reason_without_reason_detail() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "registered_reason");
        FailureReason reason = new FailureReason(id, "gui.mmcr.failure.registered_reason", 10);
        FailureReasonRegistry.register(reason);
        FailureOccurrence occurrence = FailureOccurrence.at(reason, id, FailurePhase.CAPABILITY_COMMIT,
                null, null, Map.of("available", "0"));
        ExecutionStatus status = ExecutionStatus.blocked(id, id, occurrence);

        assertEquals(reason, status.reason());
        assertEquals("0", status.details().get("available"));
        assertEquals(null, status.details().get("reason"));
    }

    @Test
    void resolve_returns_unknown_for_null_or_missing_ids() {
        assertEquals(BuiltinFailureReasons.UNKNOWN, FailureReasonRegistry.resolve(null));
        assertEquals(BuiltinFailureReasons.UNKNOWN,
                FailureReasonRegistry.resolve(ResourceLocation.fromNamespaceAndPath("old", "removed_reason")));
    }
}
