// Here I will introduce more advanced features

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_cracker") // First, create your machine definition
        .displayNameKey("machine.mmcr_kubejs.kubejs_cracker") //
        .recipeFamily("mmcr_kubejs:kubejs_cracker") //
        .allowVerticalFacing() // This allows your controller to be placed in vertical face
        .fullyRotationallySymmetric() // This allows your structure to be spined when placed vertically

    builder.register()


    // Move to server_scripts/structure/A_Vertical_Machine.js see how to define a vertical-able structure for your machine
})