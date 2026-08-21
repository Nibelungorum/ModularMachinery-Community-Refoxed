// Recently, the machine structure is allowed hot reload during the game running

// First, Listen the MMCREvents.server
MMCREvents.server(event => {

    // to make everything easier, it's better to use this api
    const api = event.getAPI()

    // Then use this function create a structure for your machine
    event
        .createStructure("mmcr_kubejs:kubejs_blast_furnace") // This Identifier point to the machine we register in startup_scripts/structure/A_Simple_Machine.js

        // Here is some stpes

        // 1. Make a structure slice
        // It's actually a two-dimensional array
        // In this example we use a structure exported by Export Tool
        // It's difficult to explain how the slices works, but in MMCR, that's not important
        // You just need copy the export content and use it
        // this function is called continuously
        .pattern(['AXA', 'XIX', 'XXX'])
        .pattern(['XXX', 'I I', 'XBX'])
        .pattern(['AXA', 'XCX', 'XXX'])

        // 2. Declare what the character meaning
        // Use function "set" to declare what block the character is
        // this function is also called continuously
        .set('X',api.block('mmcr:basic_casing')) // use a block's identifier with api.block()
        .set('A',api.anyOf(api.block('minecraft:iron_block'),api.block('minecraft:stone'),api.block('mmcr:parallel_controller_normal'),api.block('mmcr:factory_controller'))) // this combines a lot of predicates, always used for ports position
        // parallel_controller_normal is the origin of parallel capability
        // factory_controller is the origin of multi thread capability
        .set('I',api.anyOf(api.block('mmcr:item_input_bus_normal'),api.block('mmcr:item_output_bus_normal'),api.block('mmcr:energy_input_hatch_normal'))) // this is a fixed use of the ports, actually you can combine your special ports
        .set('B',api.tag('c:storage_blocks')) // you can also use tag
        // The machine use 'C' as the controller for default ! or you can set it to a standard block
        .set('C',api.block('mmcr_kubejs:kubejs_blast_furnace_controller')) // base on your machine definition, with a '_controller' suffix

        // 3. call build
        .build()
})