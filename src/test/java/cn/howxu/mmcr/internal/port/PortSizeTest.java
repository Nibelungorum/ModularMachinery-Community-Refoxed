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
                .containsExactly(8000, 12000, 16000, 32000, 64000, 128000, 512000, Integer.MAX_VALUE);
        assertThat(FluidHatchSize.values()).extracting(FluidHatchSize::transfer)
                .containsExactly(100, 200, 400, 1000, 2400, 3200, 6400, 40000);
    }

    @Test
    void energyHatchSizesMatchMmce() {
        assertThat(EnergyHatchSize.values()).extracting(EnergyHatchSize::id)
                .containsExactly("tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous", "ultimate");
        assertThat(EnergyHatchSize.values()).extracting(EnergyHatchSize::capacity)
                .containsExactly(400000, 1000000, 1600000, 6400000, 25600000, 102400000, 256000000,
                        Integer.MAX_VALUE);
        assertThat(EnergyHatchSize.values()).extracting(EnergyHatchSize::transfer)
                .containsExactly(1000, 1200, 1600, 6400, 25600, 102400, 256000, 4000000);
    }
}
