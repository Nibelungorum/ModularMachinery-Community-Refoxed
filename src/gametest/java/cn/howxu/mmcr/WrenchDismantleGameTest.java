package cn.howxu.mmcr;

import appeng.core.definitions.AEItems;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Verifies wrench dismantling for MMCR blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
public class WrenchDismantleGameTest {
    public void wrenchWhileStandingDoesNotDismantle(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.CASING.get().defaultBlockState());

        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        BlockPos worldPos = helper.absolutePos(pos);
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player,
                InteractionHand.MAIN_HAND, worldPos, new BlockHitResult(Vec3.atCenterOf(worldPos), Direction.UP, worldPos, false));

        NeoForge.EVENT_BUS.post(event);

        helper.assertTrue(!event.isCanceled(), "Wrench interaction while standing is not intercepted");
        helper.assertTrue(helper.getLevel().getBlockState(worldPos).is(ModBlocks.CASING.get()),
                "Wrench interaction while standing leaves the MMCR block in place");
        helper.succeed();
    }

    public void wrenchDismantlesItemBusAndDropsItsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        ItemBusBlockEntity bus = helper.getBlockEntity(pos);
        bus.nativeItemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 3L);

        ServerPlayer player = player(helper);
        player.setPose(Pose.CROUCHING);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        BlockPos worldPos = helper.absolutePos(pos);
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player,
                InteractionHand.MAIN_HAND, worldPos, new BlockHitResult(Vec3.atCenterOf(worldPos), Direction.UP, worldPos, false));

        NeoForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isCanceled(), "Crouch-right-clicking an MMCR block with a wrench is intercepted");
        helper.assertTrue(helper.getLevel().getBlockState(worldPos).isAir(), "The wrench dismantles the MMCR block");
        helper.assertTrue(player.getInventory().contains(new ItemStack(ModBlocks.BLOCKS.get("item_input_bus").get())),
                "Wrench dismantling returns the block itself to the player's inventory");
        helper.runAfterDelay(1, () -> {
            long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(worldPos).inflate(1))
                    .stream().filter(entity -> entity.getItem().is(Items.IRON_INGOT))
                    .mapToLong(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(dropped == 3, "Wrench dismantling drops all item bus contents");
            helper.succeed();
        });
    }

    public void wrenchDismantlingPlaysSoundOnlyOnSuccess(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.CASING.get().defaultBlockState());
        BlockPos worldPos = helper.absolutePos(pos);
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mmcr-wrench-sound"));
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        List<PlayLevelSoundEvent.AtPosition> sounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = sound -> {
            if (sound.getLevel() == helper.getLevel()
                    && sound.getPosition().equals(Vec3.atCenterOf(worldPos))
                    && sound.getSound() != null && sound.getSound().value() == SoundEvents.ITEM_FRAME_REMOVE_ITEM) {
                sounds.add(sound);
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(worldPos), Direction.UP, worldPos, false);
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, worldPos, hit));
            helper.assertTrue(sounds.isEmpty(), "Standing wrench interaction does not play the dismantling sound");

            player.setPose(Pose.CROUCHING);
            PlayerInteractEvent.RightClickBlock canceled = new PlayerInteractEvent.RightClickBlock(player,
                    InteractionHand.MAIN_HAND, worldPos, hit);
            canceled.setCanceled(true);
            NeoForge.EVENT_BUS.post(canceled);
            helper.assertTrue(sounds.isEmpty(), "Canceled wrench interaction does not play the dismantling sound");

            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, worldPos, hit));
            helper.assertTrue(helper.getLevel().getBlockState(worldPos).isAir(), "The wrench dismantles the block");
            helper.assertTrue(sounds.size() == 1, "Successful dismantling plays the AE2-style sound exactly once");
            PlayLevelSoundEvent.AtPosition sound = sounds.getFirst();
            helper.assertTrue(sound.getSource() == SoundSource.BLOCKS
                            && sound.getOriginalVolume() == 0.7F && sound.getOriginalPitch() == 1.0F,
                    "Dismantling uses the AE2 sound category, volume and pitch");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-wrench-gametest".getBytes(StandardCharsets.UTF_8)), "mmcr-wrench"),
                ClientInformation.createDefault());
    }
}
