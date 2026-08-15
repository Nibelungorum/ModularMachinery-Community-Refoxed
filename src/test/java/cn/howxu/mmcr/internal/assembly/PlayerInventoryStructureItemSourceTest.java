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

/**
 * @author howxu <dev@howxu.cn>
 */
class PlayerInventoryStructureItemSourceTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void canExtractAllCountsMatchingStacksWithoutMutating() {
        List<ItemStack> inventory = new ArrayList<>();
        inventory.add(new ItemStack(Items.IRON_BLOCK, 3));
        inventory.add(new ItemStack(Items.IRON_BLOCK, 2));

        var source = PlayerInventoryStructureItemSource.forStacks(inventory);

        assertTrue(source.canExtractAll(List.of(new ItemStack(Items.IRON_BLOCK, 5))));
        assertEquals(3, inventory.get(0).getCount());
        assertEquals(2, inventory.get(1).getCount());
    }

    @Test
    void extractAllShrinksOnlyWhenAllRequirementsAreAvailable() {
        List<ItemStack> inventory = new ArrayList<>();
        inventory.add(new ItemStack(Items.IRON_BLOCK, 3));
        inventory.add(new ItemStack(Items.COPPER_BLOCK, 1));

        var source = PlayerInventoryStructureItemSource.forStacks(inventory);

        assertFalse(source.extractAll(List.of(new ItemStack(Items.IRON_BLOCK, 4))));
        assertEquals(3, inventory.get(0).getCount());

        assertTrue(source.extractAll(List.of(new ItemStack(Items.IRON_BLOCK, 2), new ItemStack(Items.COPPER_BLOCK, 1))));
        assertEquals(1, inventory.get(0).getCount());
        assertTrue(inventory.get(1).isEmpty());
    }

    @Test
    void copyStacksReturnsIndependentInventorySnapshot() {
        List<ItemStack> inventory = new ArrayList<>();
        inventory.add(new ItemStack(Items.IRON_BLOCK, 3));

        var source = PlayerInventoryStructureItemSource.forStacks(inventory);
        var snapshot = source.copyStacks();
        snapshot.getFirst().shrink(1);

        assertEquals(3, inventory.getFirst().getCount());
    }
}
