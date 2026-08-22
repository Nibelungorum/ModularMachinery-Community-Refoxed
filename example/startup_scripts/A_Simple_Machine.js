// Register a machine definition during the startup event.
// A controller block is generated automatically after registration.

// Listen for the MMCREvents.startup event.
MMCREvents.startup(event => {


    // Create the machine definition.
    const builder = event
        .createMachine("mmcr_kubejs:kubejs_blast_furnace") // Global machine identifier.
        .displayNameKey("machine.mmcr_kubejs.kubejs_blast_furnace") // Translation key for the display name.
        .recipeFamily("mmcr_kubejs:kubejs_blast_furnace"); // Recipe family used by the machine.


    // Configure the machine properties.
    builder
        .allowMultithreading() // Allow the machine to process recipes across multiple threads.
        .allowParallelism() // Allow the machine to process recipes in parallel.
        .maxParallelAmount(2147483647) // Set the maximum parallel amount; defaults to 1 when omitted.


    // Register the machine after configuration.
    builder.register()


    // See server_scripts/structure/A_Simple_Machine.js for the structure definition.
})
