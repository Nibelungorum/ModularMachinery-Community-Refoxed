package cn.howxu.mmcr.internal.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortSizeTest {

    @Test
    void itemBusSizesMatchMmce() {
        assertThat(ItemBusSize.values()).extracting(ItemBusSize::id)
                .containsExactly("tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous");
        assertThat(ItemBusSize.values()).extracting(ItemBusSize::slots)
                .containsExactly(1, 4, 6, 9, 12, 16, 32);
    }

    @Test
    void fluidHatchSizesMatchMmce() {
        assertThat(FluidHatchSize.values()).extracting(FluidHatchSize::id)
                .containsExactly("tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous", "vacuum");
        assertThat(FluidHatchSize.values()).extracting(FluidHatchSize::capacity)
                .containsExactly(100, 400, 1000, 2000, 4500, 8000, 16000, 32000);
    }

    @Test
    void energyHatchSizesMatchMmce() {
        assertThat(EnergyHatchSize.values()).extracting(EnergyHatchSize::id)
                .containsExactly("tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous", "ultimate");
        assertThat(EnergyHatchSize.values()).extracting(EnergyHatchSize::capacity)
                .containsExactly(2048, 4096, 8192, 16384, 32768, 131072, 524288, 2097152);
        assertThat(EnergyHatchSize.values()).extracting(EnergyHatchSize::transfer)
                .containsExactly(128, 512, 512, 2048, 8192, 32768, 131072, 131072);
    }
}
