package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the blueprint item registration.
 *
 * @author howxu <dev@howxu.cn>
 */
class BlueprintRegistrationTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void blueprintIsAnOrdinaryRegisteredItem() {
        Item blueprint = ModItems.BLUEPRINT.get();

        assertEquals("blueprint", BuiltInRegistries.ITEM.getKey(blueprint).getPath());
        assertEquals(64, blueprint.getDefaultMaxStackSize());
        assertSame(blueprint, ModItems.ITEMS.get("blueprint").get());
    }
}
