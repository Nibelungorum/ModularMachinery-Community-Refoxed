package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineRegistrationTest {

    @Test
    void defaultsDisableMultithreadingAndParallelism() {
        MachineRegistration registration = MachineRegistration.builder(MMCR.id("defaults_machine")).build();

        assertThat(registration.allowMultithreading()).isFalse();
        assertThat(registration.allowParallelism()).isFalse();
        assertThat(registration.maxParallelAmount()).isEqualTo(1);
    }

    @Test
    void builderPreservesIndependentConcurrencyFlags() {
        MachineRegistration multithreadingOnly = MachineRegistration.builder(MMCR.id("threads_only"))
                .allowMultithreading(true)
                .allowParallelism(false)
                .maxParallelAmount(8)
                .build();
        MachineRegistration parallelismOnly = MachineRegistration.builder(MMCR.id("parallel_only"))
                .allowMultithreading(false)
                .allowParallelism(true)
                .maxParallelAmount(16)
                .build();
        MachineRegistration both = MachineRegistration.builder(MMCR.id("threads_and_parallel"))
                .allowMultithreading(true)
                .allowParallelism(true)
                .maxParallelAmount(32)
                .build();
        MachineRegistration neither = MachineRegistration.builder(MMCR.id("no_concurrency"))
                .allowMultithreading(false)
                .allowParallelism(false)
                .maxParallelAmount(64)
                .build();

        assertThat(multithreadingOnly.allowMultithreading()).isTrue();
        assertThat(multithreadingOnly.allowParallelism()).isFalse();
        assertThat(multithreadingOnly.maxParallelAmount()).isEqualTo(8);
        assertThat(parallelismOnly.allowMultithreading()).isFalse();
        assertThat(parallelismOnly.allowParallelism()).isTrue();
        assertThat(parallelismOnly.maxParallelAmount()).isEqualTo(16);
        assertThat(both.allowMultithreading()).isTrue();
        assertThat(both.allowParallelism()).isTrue();
        assertThat(both.maxParallelAmount()).isEqualTo(32);
        assertThat(neither.allowMultithreading()).isFalse();
        assertThat(neither.allowParallelism()).isFalse();
        assertThat(neither.maxParallelAmount()).isEqualTo(64);
    }

    @Test
    void maxParallelAmountIsClampedToAtLeastOne() {
        MachineRegistration zero = MachineRegistration.builder(MMCR.id("zero_parallel"))
                .maxParallelAmount(0)
                .build();
        MachineRegistration negative = MachineRegistration.builder(MMCR.id("negative_parallel"))
                .maxParallelAmount(-4)
                .build();

        assertThat(zero.maxParallelAmount()).isEqualTo(1);
        assertThat(negative.maxParallelAmount()).isEqualTo(1);
    }
}
