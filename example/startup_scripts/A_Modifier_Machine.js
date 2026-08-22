// Use MMCR's machine modifier system.

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_alloy_furnace") // Create the machine definition.
        .displayNameKey("machine.mmcr_kubejs.kubejs_alloy_furnace") // Translation key for the display name.
        .recipeFamily("mmcr_kubejs:kubejs_alloy_furnace") // Bind the recipe family.
        .appearance('minecraft:bricks') // Set the controller's base appearance.
        .allowModifiers() // Allow the machine to use modifiers.

    builder.register()


    // See server_scripts/structure/A_Modifier_Machine.js for the modifier structure.
})
