// Use MMCR's machine modifier system.

MMCREvents.startup(event => {

    const api = event.getAPI()

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_alloy_furnace") // Create the machine definition.
        .displayNameKey("machine.mmcr_kubejs.kubejs_alloy_furnace") // Translation key for the display name.
        .recipeFamily("mmcr_kubejs:kubejs_alloy_furnace") // Bind the recipe family.
        .appearance('minecraft:bricks') // Set the controller's base appearance.
        .allowModifiers() // Allow the machine to use modifiers.

    builder.register()

    event.registerModifier(
        "mmcr_kubejs:alloy_furnace_emerald_speedup",
        api.modifierDefinition([
            api.modifier("duration", "input", 0.5, "multiply", false)
        ])
    )

    event.registerModifierItem(
        Item.of("minecraft:emerald_block"),
        "mmcr_kubejs:alloy_furnace_emerald_speedup"
    )

    event.registerModifier(
        "mmcr_kubejs:alloy_furnace_lapis_doubling",
        api.modifierDefinition([
            api.modifier("item", "output", 2, "multiply", false)
        ])
    )

    event.registerModifierItem(
        Item.of("minecraft:lapis_block"),
        "mmcr_kubejs:alloy_furnace_lapis_doubling"
    )

    // See server_scripts/structure/A_Modifier_Machine.js for the modifier structure.
})
