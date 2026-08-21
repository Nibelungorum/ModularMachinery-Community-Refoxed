// Recently, the machine structure is allowed hot reload during the game running

// First, Listen the MMCREvents.server
MMCREvents.server(event => {


    // Then use this function create a structure for your machine
    event
        .createStructure("mmcr_kubejs:kubejs_blast_furnace") // This Identifier point to the machine we register in startup_scripts/structure/A_Simple_Machine.js

        // call this function to create a structure
        // it has 5 arguments, but we just need some of it
        .fullStructure(

            // 1. Structure slice
            // It's a two-dimensional array
            // This is a structure export by Export Tool
            // It's difficult to explain how the slices works, but in MMCR, that's not important
            // You just need copy the export content here
            [
            ['AXA', 'XIX', 'XXX'],
            ['XXX', 'I I', 'XBX'],
            ['AXA', 'XCX', 'XXX']
            ],

            // 2.

        )
})