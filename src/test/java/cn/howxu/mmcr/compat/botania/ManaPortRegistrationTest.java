package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies direction/family declarations without content registration. @author howxu <dev@howxu.cn> */
class ManaPortRegistrationTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void declarationsKeepNativeDependencyAndFixedFamilyDirections() {
        for (IOType io : List.of(IOType.INPUT, IOType.OUTPUT)) {
            ManaPortKind kind = new ManaPortKind(io == IOType.INPUT ? BotaniaManaIds.INPUT : BotaniaManaIds.OUTPUT, io);
            assertEquals(List.of("botania"), kind.modDependencies());
            assertEquals(io, kind.ioType());
            assertEquals(BotaniaManaIds.MANA, kind.families().getFirst().familyId());
            assertEquals(List.of(io == IOType.INPUT ? "mana_input_pool" : "mana_output_pool"),
                    kind.families().getFirst().countAliases());
            assertTrue(BotaniaBridgeBootstrap.selectForTesting(false).portKinds().isEmpty());
        }
    }

    @Test
    void loadedBridgeDeclaresBothPoolsWithoutAccessingContentRegistries() {
        BotaniaBridge bridge = BotaniaBridgeBootstrap.selectForTesting(true);
        assertTrue(bridge.available());
        assertEquals(List.of(new ManaPortKind(BotaniaManaIds.INPUT, IOType.INPUT),
                new ManaPortKind(BotaniaManaIds.OUTPUT, IOType.OUTPUT)), bridge.portKinds());
        for (var kind : bridge.portKinds()) {
            var binding = kind.definition().bindings().getFirst();
            assertEquals(BotaniaManaIds.TYPE, binding.type());
            assertTrue(binding.directions().supports(kind.ioType()));
            assertFalse(binding.directions().supports(kind.ioType() == IOType.INPUT ? IOType.OUTPUT : IOType.INPUT));
        }
    }
}
