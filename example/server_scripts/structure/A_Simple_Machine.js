// Machine structures support hot reload while the game is running.

// Listen for the MMCREvents.server event.
MMCREvents.server(event => {

    // Get the structure API to simplify the definitions below.
    const api = event.getAPI()

    // Create a structure using the same machine identifier as the startup script.
    event
        .createStructure("mmcr_kubejs:kubejs_blast_furnace") // Machine registered in startup_scripts/A_Simple_Machine.js.

        // 1. Define the structure slices. Each slice is a two-dimensional string array.
        // The output from the export tool can be pasted here directly.
        .pattern(['AXA', 'XIX', 'XXX'])
        .pattern(['XXX', 'I I', 'XBX'])
        .pattern(['AXA', 'XCX', 'XXX'])

        // 2. Assign a meaning to each character in the structure.
        .set('X',api.block('mmcr:basic_casing')) // Match the specified block with api.block().
        .set('A',api.anyOf(api.block('minecraft:iron_block'),api.block('minecraft:stone'),api.block('mmcr:parallel_controller_normal'),api.block('mmcr:factory_controller'))) // Combine predicates for a port position.
        // A accepts regular blocks, parallel controllers, and the multithreading controller.
        .set('I',api.anyOf(api.block('mmcr:item_input_bus'),api.block('mmcr:item_output_bus'),api.block('mmcr:energy_input_hatch'))) // Define the blocks allowed at fixed port positions.
        .set('B',api.tag('c:natural_logs')) // Block tags can also be used; this tag cannot be shared.
        .controller('C') // Assign C as the controller position.

        // 3. Build the structure.
        .build()
})
