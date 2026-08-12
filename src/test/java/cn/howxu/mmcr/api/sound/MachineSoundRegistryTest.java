package cn.howxu.mmcr.api.sound;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineSoundRegistryTest {

    @BeforeEach
    void setUp() throws Exception {
        TestBootstrap.bootstrap();
        MachineSoundRegistry.resetForTesting();
    }

    @Test
    void automaticSoundRegistrationDeduplicatesIdenticalIdsAndRejectsConflicts() {
        Identifier id = MMCR.id("machine.press.loop");
        MachineSoundRegistry.requestRegistration(id);
        MachineSoundRegistry.requestRegistration(id);

        assertThat(MachineSoundRegistry.requestedIds()).containsExactly(id);
        assertThatThrownBy(() -> MachineSoundRegistry.registered(id, SoundEvent.createVariableRangeEvent(id)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(id.toString());
    }
}
