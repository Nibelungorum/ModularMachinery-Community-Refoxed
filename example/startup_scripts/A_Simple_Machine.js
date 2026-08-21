// KubeJS Start up Event Register Machine Definations
// This stage will automatically register a controller block for your machine

// First, Listen the MMCREvents.startup
MMCREvents.startup(event => {


    // Then call the register function
    const builder = event
        .createMachine("mmcr_kubejs:kubejs_blast_furnace") // This is the Machine Global Identifier
        .displayNameKey("machine.mmcr_kubejs.kubejs_blast_furnace") // This is the i18n Machine Display Name
        .recipeFamily("mmcr_kubejs:kubejs_blast_furnace"); // This bind a recipe family for your machine


    // Then you can use this builder to set some value or functions
    builder
        .allowMultithreading() // This allows your machine use multi thread
        .allowParallelism() // this allows your machine use parallelism recipe processing
        .maxParallelAmount(2147483647) // this limit the parallelism max_value


    // Last you need to do is register it
    builder.register()


    // Move to server_scripts/structure/A_Simple_Machine.js see how to define a structure for your machine
})