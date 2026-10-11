package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies built-in suppliers resolve late registration handles without caching failed lookups.
 *
 * @author howxu <dev@howxu.cn>
 */
class BuiltinRegistrationTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void missing_handle_is_retried_and_successful_resolution_reuses_the_registration_handle() {
        String name = "test_late_builtin_handle";
        Supplier<? extends Block> supplier = BuiltinRegistration.block(name);
        assertThatThrownBy(supplier::get).isInstanceOf(NullPointerException.class);

        try {
            ModBlocks.BLOCKS.put(name, ModBlocks.BASIC_CASING);
            assertThat(supplier.get()).isSameAs(ModBlocks.BASIC_CASING.get());
            ModBlocks.BLOCKS.remove(name);
            assertThat(supplier.get()).isSameAs(ModBlocks.BASIC_CASING.get());
        } finally {
            ModBlocks.BLOCKS.remove(name);
        }
    }

    @Test
    void namespaced_builtin_suppliers_resolve_lazily_and_external_ids_keep_registry_resolution() {
        String name = "test_namespaced_builtin_handle";
        Supplier<? extends Block> fromString = BuiltinRegistration.block("mmcr:" + name);
        Supplier<? extends Block> fromId = BuiltinRegistration.block(MMCR.id(name));
        try {
            ModBlocks.BLOCKS.put(name, ModBlocks.BASIC_CASING);
            assertThat(fromString.get()).isSameAs(ModBlocks.BASIC_CASING.get());
            assertThat(fromId.get()).isSameAs(ModBlocks.BASIC_CASING.get());
            assertThat(BuiltinRegistration.block(ResourceLocation.parse("minecraft:stone")).get()).isSameAs(Blocks.STONE);
        } finally {
            ModBlocks.BLOCKS.remove(name);
        }
    }
}
