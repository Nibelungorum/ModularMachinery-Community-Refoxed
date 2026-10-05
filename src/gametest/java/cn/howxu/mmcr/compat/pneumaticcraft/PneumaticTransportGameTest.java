package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirPortBlockEntity;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.Unpooled;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static cn.howxu.mmcr.compat.pneumaticcraft.PneumaticGameTestFixtures.*;

/** Deterministic native transport, persistence and mutation coverage. @author howxu <dev@howxu.cn> */
public final class PneumaticTransportGameTest {
    public void nativeTubeEqualizationAndCacheLifecycle(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(1, 1, 1);
        BlockPos tubePos = new BlockPos(2, 1, 1);
        AirPortBlockEntity input = port(helper, inputPos, IOType.INPUT);
        AirPortBlockEntity output = port(helper, new BlockPos(3, 1, 1), IOType.OUTPUT);
        var tube = tube(helper, tubePos, Direction.WEST, Direction.EAST);
        IAirHandlerMachine in = air(helper, input, null);
        IAirHandlerMachine out = air(helper, output, null);
        IAirHandlerMachine pipe = air(helper, tube, null);
        assertSharedViews(helper, input);
        assertSharedViews(helper, output);

        pipe.addAir(pipe.getVolume() * 3);
        long total = total(in, pipe, out);
        tickNative(helper, tube);
        helper.assertTrue(in.getAir() > 0 && out.getAir() > 0 && total(in, pipe, out) == total,
                "Sealed real tube delivers air to both recipe directions and conserves signed air");
        helper.assertTrue(input.airCapability().state().air() == in.getAir(),
                "Neighbour's direct addAir is immediately visible to recipe queries");

        setAir(in, 30_000);
        setAir(pipe, 0);
        setAir(out, 0);
        total = total(in, pipe, out);
        tickNative(helper, input);
        helper.assertTrue(pipe.getAir() > 0 && in.getAir() < 30_000 && total(in, pipe, out) == total,
                "Reverse pressure gradient lets an input interface supply the native tube");

        setAir(in, 0);
        setAir(pipe, 0);
        setAir(out, 30_000);
        total = total(in, pipe, out);
        tickNative(helper, output);
        tickNative(helper, tube);
        helper.assertTrue(in.getAir() > 0 && out.getAir() < 30_000 && total(in, pipe, out) == total,
                "Output supplies a real tube and the tube supplies the input through native tickers");

        setAir(in, -in.getVolume() / 2);
        setAir(pipe, 0);
        setAir(out, 0);
        total = total(in, pipe, out);
        int vacuum = in.getAir();
        tickNative(helper, tube);
        helper.assertTrue(in.getAir() > vacuum && in.getAir() < 0 && pipe.getAir() < 0
                        && total(in, pipe, out) == total,
                "Native tube equalizes vacuum by conserving signed air rather than inventing air at zero");

        tube.setSideClosed(Direction.WEST, true);
        helper.assertTrue(in.getConnectedAirHandlers(input).isEmpty(), "Closing tube face invalidates native neighbour cache");
        setAir(in, 30_000);
        int blockedPipe = pipe.getAir();
        tickNative(helper, input);
        helper.assertTrue(in.getAir() == 30_000 && pipe.getAir() == blockedPipe,
                "Cached closed tube cannot receive air");
        tube.setSideClosed(Direction.WEST, false);
        helper.assertTrue(in.getConnectedAirHandlers(input).stream().anyMatch(c -> c.getAirHandler() == pipe),
                "Reopening a native face reconnects the same handler");

        helper.setBlock(tubePos, Blocks.AIR);
        helper.assertTrue(in.getConnectedAirHandlers(input).isEmpty(), "Removing a tube invalidates cached positive lookup");
        var replacement = tube(helper, tubePos, Direction.WEST);
        IAirHandlerMachine replacementAir = air(helper, replacement, null);
        helper.assertTrue(replacementAir != pipe && in.getConnectedAirHandlers(input).stream()
                        .anyMatch(c -> c.getAirHandler() == replacementAir),
                "Replaced tube resolves a new native handler without stale connection");
        total = total(in, replacementAir);
        tickNative(helper, input);
        helper.assertTrue(replacementAir.getAir() > 0 && total(in, replacementAir) == total,
                "Reconnected sealed native stores resume conservative transfer");
        helper.succeed();
    }

    public void serverTickerRunsNativeHandlerExactlyOnce(GameTestHelper helper) {
        var input = port(helper, new BlockPos(1, 1, 1), IOType.INPUT);
        var handler = air(helper, input, null);
        handler.addAir(10_000);
        handler.setSideLeaking(Direction.NORTH);
        handler.tick(input);
        int nativeSingleStep = handler.getAir();
        helper.assertTrue(nativeSingleStep < 10_000, "Real native handler performs one deterministic leaking step");
        setAir(handler, 10_000);
        tickNative(helper, input);
        helper.assertTrue(handler.getAir() == nativeSingleStep,
                "Air interface's bound server ticker invokes the same native handler exactly once");
        handler.setSideLeaking(null);
        helper.succeed();
    }

    public void nativeCompressorFeedsInputThroughTube(GameTestHelper helper) {
        var input = port(helper, new BlockPos(1, 1, 1), IOType.INPUT);
        var tube = tube(helper, new BlockPos(2, 1, 1), Direction.WEST, Direction.EAST);
        var compressor = compressor(helper, new BlockPos(3, 1, 1), Direction.WEST);
        var fuel = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, compressor.getBlockPos(), null);
        helper.assertTrue(fuel != null && fuel.insertItem(0, new ItemStack(Items.COAL), false).isEmpty(),
                "Actual compressor accepts real fuel through native inventory capability");
        var in = air(helper, input, null);
        var pipe = air(helper, tube, null);
        var source = air(helper, compressor, Direction.WEST);
        // Two explicit entrypoint calls initialize fuel usage and consume the fuel; no natural world-tick wait.
        tickNative(helper, compressor);
        tickNative(helper, compressor);
        helper.assertTrue(fuel.getStackInSlot(0).isEmpty() && total(source, pipe, in) > 0,
                "Actual compressor ticker consumes fuel and generates native air");
        // PNC ticks the handler before fuel production, so explicitly disperse the previously generated air.
        tickNative(helper, compressor);
        long total = total(source, pipe, in);
        tickNative(helper, tube);
        helper.assertTrue(in.getAir() > 0 && total(source, pipe, in) == total,
                "Compressor-generated air reaches the input through a sealed real tube without transport loss");
        helper.succeed();
    }

    public void signedPersistenceAndSyncPreserveNativeState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (IOType io : List.of(IOType.INPUT, IOType.OUTPUT)) {
            AirPortBlockEntity current = port(helper, pos, io);
            try (var observer = trackingObserver(helper, current.getBlockPos())) {
                for (int amount : new int[]{42_137, -4_321, 420_123, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                    setAir(air(helper, current, null), amount);
                    if (amount == 420_123) {
                        helper.assertTrue(current.airHandler().getPressure() > current.airHandler().getCriticalPressure(),
                                "Sync scenario includes true external overpressure at the changed effective volume");
                    }
                    current.observeAirChanges();
                    observer.flush();
                    helper.assertTrue(observer.updates(current.getBlockPos()).size() == 1,
                            "Actual observer sends a vanilla BE packet for a signed native air change");
                    var packet = observer.updates(current.getBlockPos()).getFirst();
                    var detachedClient = new AirPortBlockEntity(current.getBlockPos(), current.getBlockState(), current.kind());
                    detachedClient.handleUpdateTag(current.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                    helper.assertTrue(detachedClient.airCapability().state().equals(current.airCapability().state()),
                            "Production chunk update tag receives exact signed air, effective volume and tier");
                    setAir(detachedClient.airHandler(), 0);
                    detachedClient.onDataPacket(null, packet, helper.getLevel().registryAccess());
                    helper.assertTrue(detachedClient.airCapability().state().equals(current.airCapability().state()),
                            "Actual dispatched packet receives vacuum and overpressure without recipe validation or clamping");
                    assertRejectedUpdate(helper, current, packet.getTag());
                    assertRejectedPacket(helper, current, packet);
                    CompoundTag invalid = packet.getTag().copy();
                    invalid.getCompound("air_handler").putInt("Volume", 0);
                    assertRejectedUpdate(helper, detachedClient, invalid);
                    invalid = packet.getTag().copy();
                    invalid.getCompound("air_handler").putFloat("DangerPressure", Float.NaN);
                    assertRejectedUpdate(helper, detachedClient, invalid);
                    invalid = packet.getTag().copy();
                    invalid.getCompound("air_handler").putFloat("CriticalPressure", 0F);
                    assertRejectedUpdate(helper, detachedClient, invalid);
                    assertRejectedSync(helper, detachedClient, 0, current.airHandler().getDangerPressure());
                    assertRejectedSync(helper, detachedClient, current.airHandler().getVolume(), Float.NaN);
                    assertRejectedSync(helper, current, current.airHandler().getVolume(), current.airHandler().getDangerPressure());

                    observer.clear();
                    current.airHandler().setBaseVolume(current.airHandler().getVolume() + 3_000);
                    current.observeAirChanges();
                    observer.flush();
                    helper.assertTrue(observer.updates(current.getBlockPos()).size() == 1,
                            "Volume-only observation actually sends a vanilla packet without air or menu changes");
                    detachedClient.onDataPacket(null, observer.updates(current.getBlockPos()).getFirst(), helper.getLevel().registryAccess());
                    helper.assertTrue(detachedClient.airHandler().getAir() == amount
                                    && detachedClient.airCapability().state().equals(current.airCapability().state()),
                            "Volume-only receive preserves signed air and updates derived pressure and capacity");
                    observer.clear();
                    current.observeAirChanges();
                    observer.flush();
                    helper.assertTrue(observer.updates(current.getBlockPos()).isEmpty(), "Unchanged observations send no duplicate packet");

                    int volume = current.airHandler().getVolume();
                    CompoundTag saved = current.saveWithoutMetadata(helper.getLevel().registryAccess());
                    var stale = current.airCapability();
                    helper.setBlock(pos, Blocks.AIR);
                    current = port(helper, pos, io);
                    current.loadWithComponents(saved, helper.getLevel().registryAccess());
                    current.onLoad();
                    helper.assertTrue(current.airHandler().getAir() == amount && current.airHandler().getVolume() == volume,
                            "Legitimate server persistence restores signed extrema and effective volume without sync guards or clamping");
                    assertFailure(helper, stale.validate(0L, io == IOType.OUTPUT, 0F), AirFailureReasons.UNAVAILABLE);
                    assertSharedViews(helper, current);
                    observer.flush();
                    observer.clear();
                    current.observeAirChanges();
                    observer.flush();
                    helper.assertTrue(observer.updates(current.getBlockPos()).isEmpty(), "Disk loading establishes a baseline without sync sends");
                }
                CompoundTag legacy = current.saveWithoutMetadata(helper.getLevel().registryAccess());
                legacy.getCompound("air_handler").remove("Volume");
                int signedAir = current.airHandler().getAir();
                current.loadWithComponents(legacy, helper.getLevel().registryAccess());
                helper.assertTrue(current.airHandler().getAir() == signedAir && current.airHandler().getVolume() == 10_000,
                        "Legacy native Air/Leaking saves still load with the original fixed volume");
            }
            helper.setBlock(pos, Blocks.AIR);
        }
        helper.succeed();
    }

    public void recipeMutationRechecksDirectionPressureAndSafety(GameTestHelper helper) {
        var input = port(helper, new BlockPos(1, 1, 1), IOType.INPUT);
        var output = port(helper, new BlockPos(3, 1, 1), IOType.OUTPUT);
        var in = air(helper, input, null);
        var out = air(helper, output, null);
        in.addAir(30_000);
        helper.assertTrue(input.airCapability().validate(10_000L, false, 3F).success() && in.getAir() == 30_000,
                "Validation observes native pressure without draining");
        helper.assertTrue(input.airCapability().apply(10_000L, false, 3F).success() && in.getAir() == 20_000,
                "Commit checks pressure before draining exactly once");
        assertFailure(helper, input.airCapability().apply(0L, false, 3F), AirFailureReasons.INSUFFICIENT_PRESSURE);
        assertFailure(helper, input.airCapability().apply(20_001L, false, 0F), AirFailureReasons.INSUFFICIENT_AIR);
        assertFailure(helper, input.airCapability().apply(1L, true, 0F), AirFailureReasons.MISSING_INTERFACE);
        assertFailure(helper, output.airCapability().apply(0L, false, 0F), AirFailureReasons.MISSING_INTERFACE);
        assertFailure(helper, input.airCapability().apply(-1L, false, 0F), BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertFailure(helper, output.airCapability().apply((long) Integer.MAX_VALUE + 1L, true, 0F),
                BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertFailure(helper, input.airCapability().apply(0L, false, Float.NaN), BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        CapabilityResult offThread = CompletableFuture.supplyAsync(() -> input.airCapability().apply(1L, false, 0F)).join();
        assertFailure(helper, offThread, AirFailureReasons.UNAVAILABLE);
        helper.assertTrue(in.getAir() == 20_000 && out.getAir() == 0, "Failed mutations leave both native handlers unchanged");

        out.addAir(-3_000);
        helper.assertTrue(output.airCapability().apply(2_000L, true, 0F).success() && out.getAir() == -1_000,
                "Recipe output fills signed vacuum exactly rather than clamping it to zero");
        setAir(out, -out.getVolume() - 100);
        assertFailure(helper, output.airCapability().apply(1L, true, 0F), AirFailureReasons.OUTPUT_BLOCKED);
        helper.assertTrue(out.getAir() == -out.getVolume() - 100, "Below-vacuum persisted state rejects a non-exact floored insertion");
        var planner = new RequirementPlanner();
        for (boolean partial : List.of(false, true)) {
            var blocked = planner.plan(List.of(AirRequirement.output(1)), List.of(output.airCapability()),
                    new PlanningContext(1, 0, partial, new PlanningReservations()));
            helper.assertTrue(blocked.plan() == null && blocked.failure().reason() == AirFailureReasons.OUTPUT_BLOCKED
                            && blocked.outputSimulations().getFirst().accepted() == 0L
                            && out.getAir() == -out.getVolume() - 100,
                    "Production planning rejects an insertion below the native vacuum floor before FULL simulation");
        }
        helper.assertTrue(output.airCapability().apply(100L, true, 0F).success() && out.getAir() == -out.getVolume(),
                "Boundary insertion reaching the native vacuum floor remains exact and legal");
        long space = output.airCapability().state().outputCapacity();
        helper.assertTrue(output.airCapability().apply(space, true, 0F).success()
                        && out.getPressure() == out.getDangerPressure() && out.getPressure() > out.maxPressure(),
                "Recipe safety cap uses the native tier danger pressure, not inherited maxPressure");
        assertFailure(helper, output.airCapability().apply(1L, true, 0F), AirFailureReasons.OUTPUT_BLOCKED);
        out.addAir(10_000);
        helper.assertTrue(out.getPressure() > out.getDangerPressure(), "Native external addAir may exceed recipe safety cap");
        assertFailure(helper, output.airCapability().apply(1L, true, 0F), AirFailureReasons.OUTPUT_BLOCKED);
        helper.assertTrue(output.airCapability().apply(0L, true, 0F).success(), "Zero-rate output still has a valid interface");

        // The danger-based space may exceed int range after an external volume change, but addAir must never overflow.
        out.setBaseVolume(Integer.MAX_VALUE);
        setAir(out, Integer.MAX_VALUE);
        helper.assertTrue(output.airCapability().state().outputCapacity() > 0L, "Large native volume still has safety space");
        assertFailure(helper, output.airCapability().apply(1L, true, 0F), AirFailureReasons.OUTPUT_BLOCKED);
        helper.assertTrue(out.getAir() == Integer.MAX_VALUE, "Rejected insertion preserves native int boundary");
        var intBlocked = planner.plan(List.of(AirRequirement.output(1)), List.of(output.airCapability()),
                new PlanningContext(1, 0, true, new PlanningReservations()));
        helper.assertTrue(intBlocked.plan() == null && intBlocked.failure().reason() == AirFailureReasons.OUTPUT_BLOCKED,
                "Production planner bounds virtual stored air even when native safety capacity exceeds int range");
        out.setBaseVolume(10_000);
        setAir(out, -Integer.MAX_VALUE);
        long requested = (long) Integer.MAX_VALUE + 1L;
        var partial = planner.plan(List.of(AirRequirement.output(requested)), List.of(output.airCapability()),
                new PlanningContext(1, 0, true, new PlanningReservations()));
        helper.assertTrue(partial.plan() != null && partial.plan().outputSimulations().getFirst().accepted() == Integer.MAX_VALUE
                        && partial.plan().outputSimulations().getFirst().fit() == OutputFit.PARTIAL
                        && out.getAir() == -Integer.MAX_VALUE,
                "Deep NBT vacuum plans only one executable native int delta without altering saved signed air");
        helper.assertTrue(partial.plan().commit() && out.getAir() == 0,
                "Single-facet partial output applies the exact planned int delta without native vacuum clamping");
        setAir(out, -Integer.MAX_VALUE);
        var second = port(helper, new BlockPos(2, 1, 1), IOType.OUTPUT);
        var full = planner.plan(List.of(AirRequirement.output(requested)),
                List.of(output.airCapability(), second.airCapability()),
                new PlanningContext(1, 0, false, new PlanningReservations()));
        helper.assertTrue(full.plan() != null && full.plan().outputSimulations().getFirst().accepted() == requested
                        && full.plan().outputSimulations().getFirst().fit() == OutputFit.FULL,
                "Production planner splits int MAX plus one across distinct native facets");
        helper.assertTrue(full.plan().commit() && out.getAir() == 0 && second.airHandler().getAir() == 1,
                "Native full split commits MAX and one exactly, with one allocation per handler");
        setAir(out, 0);

        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        assertFailure(helper, input.airCapability().apply(0L, false, 0F), AirFailureReasons.UNAVAILABLE);
        helper.succeed();
    }

    public void directNativeChangesPublishSignedPressureAndCapacityWakeups(GameTestHelper helper) {
        var controller = recordingController(helper, new BlockPos(4, 1, 1));
        var input = port(helper, new BlockPos(1, 1, 1), IOType.INPUT);
        var output = port(helper, new BlockPos(3, 1, 1), IOType.OUTPUT);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        output.linkControllerAppearance(controller.getBlockPos(), null);
        input.onLoad();
        output.onLoad();
        helper.assertTrue(controller.wakeups.isEmpty(), "Loaded baselines do not publish availability events");
        air(helper, input, Direction.EAST).addAir(10_000);
        tickNative(helper, input);
        assertWakeup(helper, controller, input, ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE);
        input.observeAirChanges();
        tickNative(helper, input);
        helper.assertTrue(controller.wakeups.size() == 1, "Observation and a second unchanged native tick do not duplicate wakeups");

        controller.wakeups.clear();
        // These are test-local links, not a formed machine; parent ticker may legitimately prune them.
        input.linkControllerAppearance(controller.getBlockPos(), null);
        input.airHandler().setBaseVolume(input.airHandler().getVolume() / 2);
        input.observeAirChanges();
        assertWakeup(helper, controller, input, ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE);
        helper.assertTrue(input.airCapability().state().pressure() == input.airHandler().getPressure(),
                "Volume-only pressure change is reflected in recipe state");

        controller.wakeups.clear();
        air(helper, output, Direction.WEST).addAir(-1_000);
        tickNative(helper, output);
        assertWakeup(helper, controller, output, ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY);
        controller.wakeups.clear();
        output.linkControllerAppearance(controller.getBlockPos(), null);
        output.airHandler().addAir(-1_000);
        output.observeAirChanges();
        assertWakeup(helper, controller, output, ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY);
        controller.wakeups.clear();
        output.airHandler().setBaseVolume(output.airHandler().getVolume() * 2);
        output.observeAirChanges();
        assertWakeup(helper, controller, output, ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY);
        controller.wakeups.clear();
        try (var observer = trackingObserver(helper, output.getBlockPos())) {
            helper.assertTrue(output.airCapability().apply(1_000L, true, 0F).success(), "Recipe mutation uses the same observation baseline");
            observer.flush();
            helper.assertTrue(observer.updates(output.getBlockPos()).size() == 1,
                    "Successful recipe mutation actually dispatches the same vanilla tracking-client update");
            observer.clear();
            output.observeAirChanges();
            observer.flush();
            helper.assertTrue(observer.updates(output.getBlockPos()).isEmpty(), "Recipe apply and follow-up observation do not double-send");
            helper.assertTrue(controller.wakeups.isEmpty(), "Filling output reduces space and does not emit capacity wakeups");
        }

        var saved = output.saveWithoutMetadata(helper.getLevel().registryAccess());
        output.loadWithComponents(saved, helper.getLevel().registryAccess());
        output.observeAirChanges();
        helper.assertTrue(controller.wakeups.isEmpty(), "Loading signed air establishes a baseline without publishing wakeups");
        helper.succeed();
    }

    private static void assertSharedViews(GameTestHelper helper, AirPortBlockEntity port) {
        var capability = port.capabilitySnapshot().capabilities().getFirst();
        helper.assertTrue(bindingCapability(port) == capability && capability == port.airCapability(),
                "Real port definition factory returns the entity's unique cached capability");
        helper.assertTrue(capability.facet(PneumaticAirFacet.class).orElseThrow().queryIdentity() == port.airHandler()
                        && capability.facet(SyncFacet.class).orElseThrow() == capability
                        && capability.facet(AsyncPlanningFacet.class).isEmpty(),
                "Recipe and sync facets share the native identity and require main-thread planning");
        helper.assertTrue(air(helper, port, null) == port.airHandler(), "Unsided lookup exposes the same native handler");
        for (Direction side : Direction.values()) {
            helper.assertTrue(air(helper, port, side) == port.airHandler(), "Every face exposes the same factory-created handler");
        }
    }

    private static long total(IAirHandlerMachine... handlers) {
        long total = 0L;
        for (var handler : handlers) total += handler.getAir();
        return total;
    }

    private static void assertRejectedSync(GameTestHelper helper, AirPortBlockEntity target, int volume, float danger) {
        var before = target.airCapability().state();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            buffer.writeInt(7);
            buffer.writeInt(volume);
            buffer.writeFloat(danger);
            buffer.writeFloat(target.airHandler().getCriticalPressure());
            boolean rejected = false;
            try {
                target.airCapability().decode(buffer);
            } catch (IllegalArgumentException | IllegalStateException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected && target.airCapability().state().equals(before),
                    "Invalid volume/tier and server-bound sync are rejected before changing native state");
        } finally {
            buffer.release();
        }
    }

    private static void assertRejectedUpdate(GameTestHelper helper, AirPortBlockEntity target, CompoundTag tag) {
        CompoundTag before = target.saveWithoutMetadata(helper.getLevel().registryAccess());
        boolean rejected = false;
        try {
            target.handleUpdateTag(tag, helper.getLevel().registryAccess());
        } catch (IllegalArgumentException | IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected && before.equals(target.saveWithoutMetadata(helper.getLevel().registryAccess())),
                "Invalid volume/tier and server-bound update tags are rejected before any persisted BE state changes");
    }

    private static void assertRejectedPacket(GameTestHelper helper, AirPortBlockEntity target, ClientboundBlockEntityDataPacket packet) {
        CompoundTag before = target.saveWithoutMetadata(helper.getLevel().registryAccess());
        boolean rejected = false;
        try {
            target.onDataPacket(null, packet, helper.getLevel().registryAccess());
        } catch (IllegalArgumentException | IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected && before.equals(target.saveWithoutMetadata(helper.getLevel().registryAccess())),
                "Server-bound production packet receive cannot mutate native or inherited BE state");
    }

    private static void assertFailure(GameTestHelper helper, CapabilityResult result, FailureReason reason) {
        helper.assertTrue(!result.success() && reason.equals(result.status().reason()), "Operation returns the expected structured air failure");
    }

    private static void assertWakeup(GameTestHelper helper, RecordingController controller,
                                     AirPortBlockEntity port, ResourceAvailabilityNotifier.Reason reason) {
        helper.assertTrue(controller.wakeups.size() == 1 && controller.wakeups.getFirst().reason() == reason
                        && PneumaticIds.AIR.equals(controller.wakeups.getFirst().resource())
                        && port.getBlockPos().equals(controller.wakeups.getFirst().sourcePos()),
                "Native change publishes one correctly typed, resource-keyed, source-specific air wakeup");
    }
}
