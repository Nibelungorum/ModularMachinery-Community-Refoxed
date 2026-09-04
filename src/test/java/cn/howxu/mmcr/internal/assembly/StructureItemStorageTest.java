package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureItemStorageTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void playerInventoryStorageDoesNotMutateWhenCompleteAcceptanceFails() {
        List<ItemStack> stacks = new ArrayList<>(List.of(new ItemStack(Items.STONE, 63)));
        StructureItemStorage storage = PlayerInventoryStructureItemStorage.forStacks(stacks);

        assertFalse(storage.sink().accept(new ItemStack(Items.STONE, 2)));
        assertEquals(63, storage.source().copyStacks().getFirst().getCount());
        assertEquals(Items.STONE, storage.source().copyStacks().getFirst().getItem());
    }

    @Test
    void playerInventoryStorageSourceReflectsAcceptedStacks() {
        List<ItemStack> stacks = new ArrayList<>(List.of(ItemStack.EMPTY));
        StructureItemStorage storage = PlayerInventoryStructureItemStorage.forStacks(stacks);

        assertTrue(storage.sink().accept(new ItemStack(Items.STONE)));

        assertEquals(Items.STONE, storage.source().copyStacks().getFirst().getItem());
    }

    @Test
    void playerInventoryStorageAcceptsEmptyStacks() {
        StructureItemStorage storage = PlayerInventoryStructureItemStorage.forStacks(new ArrayList<>());

        assertTrue(storage.sink().accept(ItemStack.EMPTY));
    }
}
