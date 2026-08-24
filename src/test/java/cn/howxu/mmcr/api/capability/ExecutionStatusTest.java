package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import net.minecraft.resources.Identifier;
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
        Identifier id = Identifier.fromNamespaceAndPath("mmcr_test", "blocked");
        Identifier source = Identifier.fromNamespaceAndPath("mmcr_test", "machine");
        Map<String, String> details = new HashMap<>();
        details.put("reason", "busy");

        ExecutionStatus status = new ExecutionStatus(id, StatusSeverity.BLOCKED, source, details);
        details.put("reason", "changed");

        assertThat(status.id()).isEqualTo(id);
        assertThat(status.severity()).isEqualTo(StatusSeverity.BLOCKED);
        assertThat(status.source()).isEqualTo(source);
        assertThat(status.details()).containsEntry("reason", "busy");
        assertThatThrownBy(() -> status.details().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
