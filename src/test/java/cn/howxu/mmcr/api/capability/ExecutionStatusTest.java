package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class ExecutionStatusTest {

    @Test
    void status_preserves_identity_severity_source_and_an_immutable_details_snapshot() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "blocked");
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("mmcr_test", "machine");
        Map<String, String> details = new HashMap<>();
        details.put("available", "0");
        FailureReason reason = new FailureReason(ResourceLocation.fromNamespaceAndPath("mmcr_test", "busy"),
                "gui.mmcr.failure.busy", 10);
        FailureOccurrence occurrence = FailureOccurrence.at(reason, source, FailurePhase.CAPABILITY_COMMIT,
                null, 1, details);

        ExecutionStatus status = ExecutionStatus.blocked(id, source, occurrence);
        details.put("available", "changed");

        assertThat(status.id()).isEqualTo(id);
        assertThat(status.severity()).isEqualTo(StatusSeverity.BLOCKED);
        assertThat(status.source()).isEqualTo(source);
        assertThat(status.failure()).isSameAs(occurrence);
        assertThat(status.reason()).isSameAs(reason);
        assertThat(status.details()).containsEntry("available", "0");
        assertThat(status.details()).doesNotContainKey("reason");
        assertThatThrownBy(() -> status.details().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
