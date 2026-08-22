// Define a more complex machine that uses block states.
MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_reactor") // Create the machine definition.
        .displayNameKey("machine.mmcr_kubejs.kubejs_reactor") // Translation key for the display name.
        .recipeFamily("mmcr_kubejs:kubejs_reactor") // Bind the recipe family.
        .appearance("minecraft:blue_ice") // Set the controller's appearance block.

    builder.register()
    // See server_scripts/structure/A_BlockState_Machine.js for the block-state structure.
})
