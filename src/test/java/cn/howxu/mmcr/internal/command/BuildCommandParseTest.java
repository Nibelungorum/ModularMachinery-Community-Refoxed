package cn.howxu.mmcr.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildCommandParseTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void build_command_accepts_namespaced_machine_id() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        BuildCommand.register(dispatcher);

        var parse = dispatcher.parse("mmcr build mmcr:blast_furnace", null);

        assertThat(parse.getReader().canRead()).isFalse();
        assertThat(parse.getExceptions()).isEmpty();
    }
}
