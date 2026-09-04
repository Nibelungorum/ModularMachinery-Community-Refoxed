package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies linked appearance persistence does not dirty an entity while loading.
 * @author howxu <dev@howxu.cn>
 */
class LinkedAppearanceBlockEntityTest {
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void loading_linked_appearance_does_not_mark_entity_changed() {
        TrackingEntity entity = new TrackingEntity();
        CompoundTag serialized = new CompoundTag();
        ListTag controllers = new ListTag();
        CompoundTag controller = new CompoundTag();
        controller.putInt("X", 1);
        controller.putInt("Y", 2);
        controller.putInt("Z", 3);
        controller.putString("Texture", MMCR.id("block/test_casing").toString());
        controllers.add(controller);
        serialized.put("LinkedControllers", controllers);

        entity.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, serialized));

        assertThat(entity.changed).isZero();
        assertThat(entity.appearanceBaseTexture()).isEqualTo(MMCR.id("block/test_casing"));
    }

    private static final class TrackingEntity extends LinkedAppearanceBlockEntity {
        private int changed;

        private TrackingEntity() {
            super(ModBlockEntities.DATA_STORAGE.get(), BlockPos.ZERO,
                    ModBlocks.DATA_STORAGE.get().defaultBlockState());
        }

        @Override
        public void setChanged() {
            changed++;
        }
    }
}
