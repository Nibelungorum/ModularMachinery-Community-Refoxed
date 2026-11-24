package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.util.Icon;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** @author HowXu {@literal <dev@howxu.cn>} */
class PatternRefundButtonTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void pressDoesNotAcknowledgeItsOwnRefund() {
        var calls = new AtomicInteger();
        var button = new PatternRefundButton(calls::incrementAndGet);
        button.updateState(true);
        button.onPress(new KeyEvent(32, 0, 0));
        button.onPress(new KeyEvent(32, 0, 0));
        assertThat(calls.get()).isEqualTo(2);
        assertThat(button.getIcon()).isEqualTo(Icon.BACK);
        assertThat(button.getTooltipMessage()).containsExactly(Component.translatable("mmcr.pattern_refund.action"),
                Component.translatable("mmcr.pattern_refund.has_contents").withStyle(ChatFormatting.RED));
        button.updateState(false);
        assertThat(button.getTooltipMessage().getLast()).isEqualTo(
                Component.translatable("mmcr.pattern_refund.empty").withStyle(ChatFormatting.GREEN));
    }
}
