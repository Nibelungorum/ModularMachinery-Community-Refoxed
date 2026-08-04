# Bus/Hatch Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable base-plus-overlay block model system for current bus/hatch assets without adding unregistered tiers or gameplay blocks.

**Architecture:** Add one generic parent block model that composes a tinted casing layer and an untinted overlay layer. Update datagen so concrete bus/hatch models bind `basic_casing` as the base texture and per-port overlay textures as the overlay texture. Preserve existing composite PNGs unless a later implementation step proves they are unused.

**Tech Stack:** Minecraft/NeoForge resource JSON, vanilla block model format, existing `assets/mmcr` resource namespace.

## Global Constraints

- Build the reusable overlay model system only.
- Apply the system to the currently registered bus/hatch categories only: item input/output bus, fluid input/output hatch, and energy input/output hatch.
- Do not add unregistered MMCE tiers, ME buses, upgrade buses, or new gameplay blocks in this pass.
- Keep existing composite PNG files unless current references prove they are no longer used.
- Java/Gradle changes should stay limited to datagen code needed to emit the generated resources.

---

## File Structure

- Create `src/main/resources/assets/mmcr/models/block/bus_hatch_overlay.json`: generic two-layer full cube parent model.
- Modify `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java`: generate concrete current bus/hatch models with base and overlay textures.
- Generate concrete bus/hatch model JSON files under `src/generated/resources/assets/mmcr/models/block/` by running `runClientData`.
- Copy MMCE-style normal overlay textures into `src/main/resources/assets/mmcr/textures/block/`.
- Do not modify Java registry files unless blockstate/model names prove they do not resolve to the planned model paths.

### Task 1: Add Generic Parent Model

**Files:**
- Create: `src/main/resources/assets/mmcr/models/block/bus_hatch_overlay.json`
- Reference: `src/main/resources/assets/mmcr/models/block/machine_controller_overlay.json`

**Interfaces:**
- Consumes: vanilla block model texture keys `bg_all` and `ov_all`.
- Produces: parent model `mmcr:block/bus_hatch_overlay` for concrete block models.

- [ ] **Step 1: Inspect the existing controller overlay parent**

Run: `rtk file read src/main/resources/assets/mmcr/models/block/machine_controller_overlay.json`

Expected: JSON shows two full-cube elements, the first tinted and the second untinted.

- [ ] **Step 2: Create the generic bus/hatch parent model**

Create `src/main/resources/assets/mmcr/models/block/bus_hatch_overlay.json` with exactly this content:

```json
{
  "parent": "block/block",
  "textures": {
    "particle": "#bg_all",
    "bg_down": "#bg_all",
    "bg_up": "#bg_all",
    "bg_north": "#bg_all",
    "bg_east": "#bg_all",
    "bg_south": "#bg_all",
    "bg_west": "#bg_all",
    "ov_down": "#ov_all",
    "ov_up": "#ov_all",
    "ov_north": "#ov_all",
    "ov_east": "#ov_all",
    "ov_south": "#ov_all",
    "ov_west": "#ov_all"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down":  { "texture": "#bg_down",  "cullface": "down",  "tintindex": 1 },
        "up":    { "texture": "#bg_up",    "cullface": "up",    "tintindex": 1 },
        "north": { "texture": "#bg_north", "cullface": "north", "tintindex": 1 },
        "south": { "texture": "#bg_south", "cullface": "south", "tintindex": 1 },
        "west":  { "texture": "#bg_west",  "cullface": "west",  "tintindex": 1 },
        "east":  { "texture": "#bg_east",  "cullface": "east",  "tintindex": 1 }
      }
    },
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down":  { "texture": "#ov_down",  "cullface": "down" },
        "up":    { "texture": "#ov_up",    "cullface": "up" },
        "north": { "texture": "#ov_north", "cullface": "north" },
        "south": { "texture": "#ov_south", "cullface": "south" },
        "west":  { "texture": "#ov_west",  "cullface": "west" },
        "east":  { "texture": "#ov_east",  "cullface": "east" }
      }
    }
  ]
}
```

- [ ] **Step 3: Validate JSON syntax**

Run: `python -m json.tool src/main/resources/assets/mmcr/models/block/bus_hatch_overlay.json >/tmp/opencode/bus_hatch_overlay.json`

Expected: command exits 0 and writes formatted JSON to `/tmp/opencode/bus_hatch_overlay.json`.

### Task 2: Generate Concrete Bus/Hatch Models

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java`
- Generate: `src/generated/resources/assets/mmcr/models/block/item_input_bus.json`
- Generate: `src/generated/resources/assets/mmcr/models/block/item_output_bus.json`
- Generate: `src/generated/resources/assets/mmcr/models/block/fluid_input_hatch.json`
- Generate: `src/generated/resources/assets/mmcr/models/block/fluid_output_hatch.json`
- Generate: `src/generated/resources/assets/mmcr/models/block/energy_input_hatch.json`
- Generate: `src/generated/resources/assets/mmcr/models/block/energy_output_hatch.json`

**Interfaces:**
- Consumes: parent model `mmcr:block/bus_hatch_overlay` from Task 1.
- Produces: generated concrete model IDs matching current expected block model names.

- [ ] **Step 1: Add I/O port texture slot and model template**

In `ModelGen`, add `TextureSlot OV_ALL` and `ModelTemplate BUS_HATCH_OVERLAY` using parent `MMCR.id("block/bus_hatch_overlay")` and slots `BG_ALL, OV_ALL`.

- [ ] **Step 2: Detect current I/O ports from `PortKinds`**

Add `private static boolean isIoPort(String blockName)` that returns true when `PortKinds.all()` contains a kind with the same `id()`.

- [ ] **Step 3: Generate overlay models for I/O ports**

In `registerModels`, before the trivial cube fallback, add an `isIoPort(name)` branch that maps `BG_ALL` to `mmcr:block/basic_casing`, maps `OV_ALL` through an explicit MMCE-style normal overlay mapping, creates the block model with `BUS_HATCH_OVERLAY`, emits a plain blockstate, and registers the simple item model.

The overlay mapping is:

```text
item_input_bus -> overlay_inputbus_normal
item_output_bus -> overlay_outputbus_normal
fluid_input_hatch -> overlay_fluidinputhatch_normal
fluid_output_hatch -> overlay_fluidoutputhatch_normal
energy_input_hatch -> overlay_energyinputhatch_normal
energy_output_hatch -> overlay_energyoutputhatch_normal
```

- [ ] **Step 4: Regenerate resources**

Run: `./gradlew runClientData --no-daemon`

Expected: datagen completes successfully and writes six concrete bus/hatch model files under `src/generated/resources/assets/mmcr/models/block/`.

- [ ] **Step 5: Inspect generated models**

Verify each generated block model has this shape, with the matching `overlay_<name>` texture:

```json
{
  "parent": "mmcr:block/bus_hatch_overlay",
  "textures": {
    "bg_all": "mmcr:block/basic_casing",
    "ov_all": "mmcr:block/overlay_item_input_bus"
  }
}
```

- [ ] **Step 6: Validate generated concrete model JSON files**

Run: `python -m json.tool src/generated/resources/assets/mmcr/models/block/item_input_bus.json >/tmp/opencode/item_input_bus.json`

Run: `python -m json.tool src/generated/resources/assets/mmcr/models/block/item_output_bus.json >/tmp/opencode/item_output_bus.json`

Run: `python -m json.tool src/generated/resources/assets/mmcr/models/block/fluid_input_hatch.json >/tmp/opencode/fluid_input_hatch.json`

Run: `python -m json.tool src/generated/resources/assets/mmcr/models/block/fluid_output_hatch.json >/tmp/opencode/fluid_output_hatch.json`

Run: `python -m json.tool src/generated/resources/assets/mmcr/models/block/energy_input_hatch.json >/tmp/opencode/energy_input_hatch.json`

Run: `python -m json.tool src/generated/resources/assets/mmcr/models/block/energy_output_hatch.json >/tmp/opencode/energy_output_hatch.json`

Expected: every command exits 0.

### Task 3: Derive Overlay Texture Files

**Files:**
- Read: `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_inputbus_normal.png`
- Read: `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_outputbus_normal.png`
- Read: `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_fluidinputhatch_normal.png`
- Read: `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_fluidoutputhatch_normal.png`
- Read: `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_energyinputhatch_normal.png`
- Read: `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_energyoutputhatch_normal.png`
- Create: `src/main/resources/assets/mmcr/textures/block/overlay_inputbus_normal.png`
- Create: `src/main/resources/assets/mmcr/textures/block/overlay_outputbus_normal.png`
- Create: `src/main/resources/assets/mmcr/textures/block/overlay_fluidinputhatch_normal.png`
- Create: `src/main/resources/assets/mmcr/textures/block/overlay_fluidoutputhatch_normal.png`
- Create: `src/main/resources/assets/mmcr/textures/block/overlay_energyinputhatch_normal.png`
- Create: `src/main/resources/assets/mmcr/textures/block/overlay_energyoutputhatch_normal.png`

**Interfaces:**
- Consumes: MMCE normal overlay PNGs as migration sources.
- Produces: overlay texture paths referenced by Task 2 generated models.

- [ ] **Step 1: Confirm source PNG dimensions**

Run: `file reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_inputbus_normal.png reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_outputbus_normal.png reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_fluidinputhatch_normal.png reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_fluidoutputhatch_normal.png reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_energyinputhatch_normal.png reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_energyoutputhatch_normal.png`

Expected: every file is a PNG image.

- [ ] **Step 2: Copy MMCE normal overlay PNGs**

Copy the six MMCE normal overlay files into the MMCR texture directory without renaming their MMCE-style identifiers.

Required output files:

```text
src/main/resources/assets/mmcr/textures/block/overlay_inputbus_normal.png
src/main/resources/assets/mmcr/textures/block/overlay_outputbus_normal.png
src/main/resources/assets/mmcr/textures/block/overlay_fluidinputhatch_normal.png
src/main/resources/assets/mmcr/textures/block/overlay_fluidoutputhatch_normal.png
src/main/resources/assets/mmcr/textures/block/overlay_energyinputhatch_normal.png
src/main/resources/assets/mmcr/textures/block/overlay_energyoutputhatch_normal.png
```

- [ ] **Step 3: Confirm output PNG dimensions and alpha support**

Run: `file src/main/resources/assets/mmcr/textures/block/overlay_inputbus_normal.png src/main/resources/assets/mmcr/textures/block/overlay_outputbus_normal.png src/main/resources/assets/mmcr/textures/block/overlay_fluidinputhatch_normal.png src/main/resources/assets/mmcr/textures/block/overlay_fluidoutputhatch_normal.png src/main/resources/assets/mmcr/textures/block/overlay_energyinputhatch_normal.png src/main/resources/assets/mmcr/textures/block/overlay_energyoutputhatch_normal.png`

Expected: every output file is a PNG image with the same dimensions as its source.

### Task 4: Verify Resource Coverage

**Files:**
- Read: `src/main/resources/assets/mmcr/blockstates/*.json` if present.
- Read: `src/main/resources/assets/mmcr/models/item/*.json` if present.
- Verify: all files created in Tasks 1-3.

**Interfaces:**
- Consumes: model and texture resources from Tasks 1-3.
- Produces: confidence that current block IDs resolve to existing models and textures.

- [ ] **Step 1: Locate blockstate and item model files**

Run: `rtk ls src/main/resources/assets/mmcr/blockstates`

Expected: generated blockstate files show whether current block IDs refer to model names generated in Task 2.

Run: `rtk ls src/main/resources/assets/mmcr/models/item`

Expected: if the directory exists, item model files show whether block item models need matching parents.

- [ ] **Step 2: Add missing blockstate files only if absent and required**

If a currently registered block has no blockstate file and no datagen output covers it, create a blockstate file using this pattern with the matching model path:

```json
{
  "variants": {
    "": {
      "model": "mmcr:block/item_input_bus"
    }
  }
}
```

Create one file per actual registered block ID only. Do not create blockstates for unregistered MMCE tiers.

- [ ] **Step 3: Validate all JSON resources touched by this plan**

Run: `python -m json.tool src/main/resources/assets/mmcr/models/block/bus_hatch_overlay.json >/tmp/opencode/bus_hatch_overlay.final.json`

Run one `python -m json.tool` command for each generated concrete model and any blockstate files created in Step 2.

Expected: every command exits 0.

- [ ] **Step 4: Run Gradle resource/build verification**

Run: `./gradlew compileJava --no-daemon`

Expected: task completes successfully. Resource-only changes should not require Java modifications.

- [ ] **Step 5: Review git diff**

Run: `rtk git diff -- src/main/resources/assets/mmcr/models/block src/main/resources/assets/mmcr/textures/block docs/superpowers/plans/2026-08-04-bus-hatch-overlay.md docs/superpowers/specs/2026-08-04-bus-hatch-overlay-design.md`

Expected: diff contains only datagen changes, the generic overlay parent, generated current bus/hatch models, overlay texture files, and planning/spec docs.
