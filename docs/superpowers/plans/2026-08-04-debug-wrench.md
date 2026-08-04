# Debug Wrench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a creative-only debug `WrenchItem` that, when right-clicked on any of the six `IOPortBlockEntity` kinds, prints the entity's internal storage to the holding player's chat.

**Architecture:** New `WrenchItem` registered directly in `ModItems` (not auto BlockItem). A new `WrenchDebugHandler` subscribed to `PlayerInteractEvent.RightClickBlock` on `NeoForge.EVENT_BUS` detects main-hand wrench + IOPort target, dispatches by subclass, sends `ServerPlayer.sendSystemMessage` for each line, and denies the original use so the IO-port menu does not open. Texture is a placeholder PNG; model and translations are produced by existing datagen.

**Tech Stack:** Java 25, NeoForge 26.1.2.84, Minecraft 26.1.2, DeferredRegister, `Event.Result` (NeoForge bus), `PlayerInteractEvent.RightClickBlock`.

## Global Constraints

- Project version: `minecraft_version=26.1.2`, `neo_version=26.1.2.84`, `mod_version=0.0.0` (`gradle.properties`).
- Source root: `src/main/java/cn/howxu/mmcr/...`.
- New Java class javadoc must contain `@author howxu <dev@howxu.cn>`.
- New class names must NOT use the `MMCR` prefix.
- Naming, packaging, and import order must follow adjacent files (e.g. `IOPortBlock.java`, `ItemBusBlockEntity.java`).
- Verify after implementation: `./gradlew compileJava --no-daemon`, then `./gradlew build --no-daemon`.
- No unit tests, no gametest — spec §5.
- Wrench texture file `assets/mmcr/textures/item/wrench.png` is the user's responsibility; if missing, model still generates but in-game shows missing-texture (acceptable per spec §4.3).

---

### Task 1: WrenchItem + registration + datagen

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/item/WrenchItem.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModItems.java:14-28`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java:8-83`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java:54-91`
- Create (placeholder): `src/main/resources/assets/mmcr/textures/item/wrench.png` — 16×16 placeholder PNG; user will replace.

**Interfaces:**
- Consumes: `cn.howxu.mmcr.MMCR.id(String)`, `cn.howxu.mmcr.registry.ModItems.REGISTER`, `cn.howxu.mmcr.registry.ModItems.ITEMS`.
- Produces: `cn.howxu.mmcr.registry.ModItems.WRENCH` (`DeferredHolder<Item, Item>`). Used by Task 2's `WrenchDebugHandler`.

- [ ] **Step 1: Create `WrenchItem.java`**

Write to `src/main/java/cn/howxu/mmcr/internal/item/WrenchItem.java`:

```java
package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * 调试扳手。右键 IO 端口在聊天栏打印内部储量,
 * 实际逻辑见 {@link cn.howxu.mmcr.internal.event.WrenchDebugHandler}。
 *
 * @author howxu <dev@howxu.cn>
 */
public class WrenchItem extends Item {

    public WrenchItem() {
        super(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, MMCR.id("wrench"))));
    }
}
```

- [ ] **Step 2: Register `WRENCH` in `ModItems.java`**

Edit `src/main/java/cn/howxu/mmcr/registry/ModItems.java`.

First, add an import after the existing `import cn.howxu.mmcr.MMCR;` line:

```java
import cn.howxu.mmcr.internal.item.WrenchItem;
```

Then in the file body, insert a new static field declaration **after** the existing `ITEMS` field and **before** the existing `static {}` block, and append a single line to the existing `static {}` block. The diff is:

Before:
```java
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<Item, Item>> ITEMS = new LinkedHashMap<>();

    static {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            DeferredHolder<Item, Item> itemHolder = REGISTER.register(name, () ->
                    new BlockItem(blockHolder.get(),
                            new Item.Properties().setId(
                                    ResourceKey.create(Registries.ITEM, MMCR.id(name)))));
            ITEMS.put(name, itemHolder);
        });
    }
```

After:
```java
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(MMCR.MODID);

    public static final DeferredHolder<Item, Item> WRENCH =
            REGISTER.register("wrench", WrenchItem::new);

    public static final LinkedHashMap<String, DeferredHolder<Item, Item>> ITEMS = new LinkedHashMap<>();

    static {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            DeferredHolder<Item, Item> itemHolder = REGISTER.register(name, () ->
                    new BlockItem(blockHolder.get(),
                            new Item.Properties().setId(
                                    ResourceKey.create(Registries.ITEM, MMCR.id(name)))));
            ITEMS.put(name, itemHolder);
        });
        ITEMS.put("wrench", WRENCH);
    }
```

`REGISTER` is a `DeferredRegister.Items` so `REGISTER.register("wrench", WrenchItem::new)` resolves to the matching `register(String, Supplier<? extends Item>)`. The new `ITEMS.put("wrench", WRENCH)` ensures `MMCR.CREATIVE_TABS.displayItems` (which iterates `ModItems.ITEMS.values()`) auto-includes the wrench.

- [ ] **Step 3: Add translation entries to `Translations.java`**

Edit `src/main/java/cn/howxu/mmcr/datagen/Translations.java`.

In the `en_us` map, after the existing `Map.entry("item.mmcr.debug_infinite_lava_source", "Debug Infinite Lava Source"),` line, insert:

```java
                    Map.entry("item.mmcr.wrench",                    "Wrench"),
```

In the `zh_cn` map, after the existing `Map.entry("item.mmcr.debug_infinite_lava_source", "调试-无限岩浆源"),` line, insert:

```java
                    Map.entry("item.mmcr.wrench",                    "调试扳手"),
```

(The leading whitespace matches the surrounding entries — 20 leading spaces before `Map.entry` since it's nested inside two `Map.ofEntries(...)` levels. Mirror the alignment of the line above.)

- [ ] **Step 4: Add item-model generation in `ModelGen.java`**

Edit `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java`.

In `registerModels`, after the closing `});` of the `ModBlocks.BLOCKS.forEach(...)` block (i.e. as the last statement of the method, before its closing brace), insert:

```java
        itemModels.generateFlatItem(ModItems.WRENCH.get(), ModelLocationUtils.getModelLocation(ModItems.WRENCH.get()));
```

The imports already cover everything needed: `ModelLocationUtils`, `ItemModelGenerators` (`itemModels`), `ModItems`.

- [ ] **Step 5: Add texture placeholder PNG**

Create `src/main/resources/assets/mmcr/textures/item/wrench.png` as a 16×16 placeholder (any solid color PNG is fine; the user will replace it). If the directory `assets/mmcr/textures/item/` does not exist, create it.

- [ ] **Step 6: Compile check**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. No errors.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/internal/item/WrenchItem.java \
        src/main/java/cn/howxu/mmcr/registry/ModItems.java \
        src/main/java/cn/howxu/mmcr/datagen/Translations.java \
        src/main/java/cn/howxu/mmcr/datagen/ModelGen.java \
        src/main/resources/assets/mmcr/textures/item/wrench.png
git commit -m "feat(item): add debug WrenchItem with datagen model and translations"
```

---

### Task 2: WrenchDebugHandler

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/event/WrenchDebugHandler.java`

**Interfaces:**
- Consumes: `cn.howxu.mmcr.registry.ModItems.WRENCH` (from Task 1), `cn.howxu.mmcr.internal.tile.IOPortBlockEntity`, `cn.howxu.mmcr.internal.tile.ItemBusBlockEntity`, `cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity`, `cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity`.
- Produces: a static `@SubscribeEvent` method subscribed to `NeoForge.EVENT_BUS` (`EventBusSubscriber.Bus.GAME`).

- [ ] **Step 1: Create `WrenchDebugHandler.java`**

Write to `src/main/java/cn/howxu/mmcr/internal/event/WrenchDebugHandler.java`:

```java
package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 监听主手持扳手右键 IO 端口,在聊天栏打印内部储量。
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchDebugHandler {

    private WrenchDebugHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.is(ModItems.WRENCH.get())) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof IOPortBlockEntity port)) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        BlockPos pos = event.getPos();

        if (port instanceof ItemBusBlockEntity bus) {
            printItemBus(player, pos, bus);
        } else if (port instanceof FluidHatchBlockEntity hatch) {
            printFluidHatch(player, pos, hatch);
        } else if (port instanceof EnergyHatchBlockEntity hatch) {
            printEnergyHatch(player, pos, hatch);
        }

        event.setUseItem(Event.Result.DENY);
        event.setUseBlock(Event.Result.DENY);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void printItemBus(ServerPlayer player, BlockPos pos, ItemBusBlockEntity bus) {
        ItemStackHandler handler = bus.getItemStackHandler(null);
        Component prefix = prefix(bus, pos);
        player.sendSystemMessage(prefix);
        int total = 0;
        int occupied = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                player.sendSystemMessage(Component.literal("  Slot " + i + ": (空)"));
            } else {
                total += stack.getCount();
                occupied++;
                player.sendSystemMessage(Component.literal("  Slot " + i + ": ")
                        .append(stack.getHoverName())
                        .append(Component.literal(" x" + stack.getCount() + "/" + stack.getMaxStackSize())));
            }
        }
        player.sendSystemMessage(Component.literal(
                "  共 " + total + " 个物品,占用 " + occupied + "/" + handler.getSlots() + " 槽"));
    }

    private static void printFluidHatch(ServerPlayer player, BlockPos pos, FluidHatchBlockEntity hatch) {
        FluidTank tank = hatch.getFluidTank(null);
        player.sendSystemMessage(prefix(hatch, pos));
        if (tank.getFluid().isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "  流体: (空) 0 / " + tank.getCapacity() + " mB"));
        } else {
            player.sendSystemMessage(Component.literal("  流体: ")
                    .append(tank.getFluid().getHoverName())
                    .append(Component.literal(
                            " " + tank.getFluid().getAmount() + " / " + tank.getCapacity() + " mB")));
        }
    }

    private static void printEnergyHatch(ServerPlayer player, BlockPos pos, EnergyHatchBlockEntity hatch) {
        EnergyStorage storage = hatch.getMutableEnergyStorage(null);
        player.sendSystemMessage(prefix(hatch, pos));
        player.sendSystemMessage(Component.literal(
                "  能量: " + storage.getEnergyStored() + " / " + storage.getMaxEnergyStored() + " FE"));
    }

    private static Component prefix(IOPortBlockEntity port, BlockPos pos) {
        Component name = Component.translatable("container.mmcr." + port.kind().id());
        return Component.literal("[MMCR] ")
                .append(name)
                .append(Component.literal(
                        " @ (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"));
    }
}
```

Notes for reviewer:
- `ItemStack.is(Item)` is the modern NeoForge equality check; equivalent to `getItem() == item` but defensive against empty stacks.
- `bus.getItemStackHandler(null)` and `hatch.getFluidTank(null)` / `getMutableEnergyStorage(null)` mirror the way `IOPortBlock.openServerMenu` reaches the underlying storage (these methods take `Direction side`, ignored).
- Server-only enforcement via `event.getLevel().isClientSide()` is checked **before** any `sendSystemMessage` so the handler can also safely cast `event.getEntity()` to `ServerPlayer`.
- Each `sendSystemMessage` is a single line — chat shows them as separate messages with the default chat line prefix.
- The cast `(ServerPlayer) event.getEntity()` is safe here: in a non-client `Level` the entity is guaranteed to be a `ServerPlayer` because `PlayerInteractEvent.RightClickBlock` only fires for actual `Player` instances, and on the server `Player` is `ServerPlayer`.

- [ ] **Step 2: Compile check**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. No errors.

- [ ] **Step 3: Full build**

Run: `./gradlew build --no-daemon`
Expected: `BUILD SUCCESSFUL`. All datagen tasks produce the wrench model and translation entries under `src/generated/resources/`.

- [ ] **Step 4: Verify generated assets**

After Step 3, confirm the following files exist:

- `src/generated/resources/assets/mmcr/models/item/wrench.json`
- `src/generated/resources/assets/mmcr/lang/en_us.json` (contains `"item.mmcr.wrench": "Wrench"`)
- `src/generated/resources/assets/mmcr/lang/zh_cn.json` (contains `"item.mmcr.wrench": "调试扳手"`)

If any are missing, re-check Step 4 of Task 1 and Step 1 of Task 2.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/internal/event/WrenchDebugHandler.java
git commit -m "feat(debug): wrench right-click prints IOPort storage to chat"
```

---

### Task 3: Manual in-game verification

**Files:** none changed.

- [ ] **Step 1: Run the client**

Run: `./gradlew runClient --no-daemon`

- [ ] **Step 2: Verify each kind**

In a creative-mode world:
1. Place one of each of the six IO ports (`item_input_bus`, `item_output_bus`, `fluid_input_hatch`, `fluid_output_hatch`, `energy_input_hatch`, `energy_output_hatch`).
2. For each port:
   - Insert some content (e.g. 32 iron ingots into item bus, fill the fluid hatch with water, charge the energy hatch).
   - Hold the wrench in main hand and right-click the port.
   - Confirm the chat shows the lines described in spec §4.5 with correct counts, fluid, energy, and position.

- [ ] **Step 3: Verify negative paths**

With the wrench in hand:
1. Right-click a non-IOPort block (e.g. `basic_casing`): the IO-port menu must **not** open; chat shows nothing.
2. Right-click air: nothing happens.
3. Switch the wrench to the off-hand and right-click an IO port with an empty main hand: the IO-port menu opens normally.

- [ ] **Step 4: Commit (only if Step 2/3 surfaced anything needing a follow-up patch)**

If everything passes, no commit is needed. If a tweak was required, amend Task 2 and commit accordingly.