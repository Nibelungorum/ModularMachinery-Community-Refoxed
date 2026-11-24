package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** @author HowXu {@literal <dev@howxu.cn>} */
class MachineOutputPatternTest {
    @BeforeAll
    static void setup() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void hintsSelectResourcesNotCountsOrExclusiveOutputs() {
        var hint = new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F);
        var gold = new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 9), 0.5F);
        var named = new ItemStack(Items.GOLD_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        var other = new MachineOutput.ItemOutput(named, 1F);
        assertThat(MachineControllerBlockEntity.patternOutputsMatch(List.of(gold, other), List.of(hint, hint))).isTrue();
        assertThat(MachineControllerBlockEntity.patternOutputsMatch(List.of(other), List.of(hint))).isFalse();
        assertThat(hint.matchesPatternOutput(gold)).isFalse();
    }
}
