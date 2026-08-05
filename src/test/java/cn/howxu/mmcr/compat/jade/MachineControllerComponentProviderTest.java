package cn.howxu.mmcr.compat.jade;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerComponentProviderTest {

    @Test
    void does_not_depend_on_separate_snapshot_class_at_runtime() throws IOException {
        String classFile = new String(MachineControllerComponentProvider.class
                .getResourceAsStream("MachineControllerComponentProvider.class")
                .readAllBytes(), StandardCharsets.ISO_8859_1);

        assertThat(classFile).doesNotContain("MachineControllerJadeSnapshot");
    }
}
