// KubeJS Start up Event Register Machine Definations
// This stage will registe a controller block for your machine

// First, Listen the MMCREvents.startup
MMCREvents.startup(event => {
    // then call the register function
    var builder = event
                    .createMachine("mmcr_kubejs:kubejs_blast_furnace") // This is the Machine Global Identifier              
                    .displayNameKey("machine.mmcr_kubejs.kubejs_blast_furnace") // This is the i18n Machine Display Name              
                    .recipeFamily("mmcr_kubejs:kubejs_blast_furnace") // This bind a recipe family for your machine
    // then you can use this builder to set some value or functions
    builder
        .allowMultithreading() // This allows your machine use multi thread
        .allowParallelism() // this allows your machine use parallelism recipe processing
        .maxParallelAmount(2147483647) // this limit the parallelism max_value
    // last you need to do is register it
    builder.register()
})