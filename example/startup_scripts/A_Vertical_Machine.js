// Configure machine facing and rotational symmetry.

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_cracker") // Create the machine definition.
        .displayNameKey("machine.mmcr_kubejs.kubejs_cracker") // Translation key for the display name.
        .recipeFamily("mmcr_kubejs:kubejs_cracker") // Bind the recipe family.
        .allowVerticalFacing() // Allow the controller to be placed vertically.
        .fullyRotationallySymmetric() // Allow full rotation when the structure is placed vertically.

    builder.register()


    // See server_scripts/structure/A_Vertical_Machine.js for the rotatable structure.
})
