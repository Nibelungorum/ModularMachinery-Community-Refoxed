package org.nibelungorum;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultMachinesPackageTest {

    @Test
    void default_machines_are_published_from_org_nibelungorum_package() throws Exception {
        assertThat(DefaultMachines.class.getPackageName()).isEqualTo("org.nibelungorum");
        assertThatThrownBy(() -> Class.forName("cn.howxu.mmcr.internal.machine.DefaultMachines"))
                .as("Default machines must not remain in the internal mmcr package")
                .isInstanceOf(ClassNotFoundException.class);
    }
}
