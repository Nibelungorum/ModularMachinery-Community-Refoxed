MMCREvents.server(event => {
    const api = event.getAPI()

    // Store the structure in a variable so it can be used for advanced features.
    const structure = event.createStructure("mmcr_kubejs:kubejs_alloy_furnace")

    // Create a single-block modifier for the diamond block.
    const diamondSpeedup = api.singleBlockModifier(
        'alloy_furnace_diamond_speedup', // Modifier identifier used at runtime.
        api.block('minecraft:diamond_block'), // Matching predicate; other predicates can be used as well.
        // Each entry in this array defines one modifier effect.
        [
            api.modifier(
                'duration', // Modify the recipe duration.
                'input',    // Duration is processed before the recipe starts, so use the input phase.
                0.5,        // Modifier value.
                'multiply', // Modifier calculation mode.
                false       // Do not affect output chance; modify the output count instead.
            )
        ],
        Item.of('minecraft:diamond_block') // Item shown for the modifier in the JEI preview.
    )

    // Create another modifier that doubles the output count.
    const goldDoubling = api.singleBlockModifier(
        'alloy_furnace_gold_doubling',
        api.block('minecraft:gold_block'),
        [
            api.modifier(
                'item',
                'output',
                2,
                'multiply',
                false
            )
        ],
        Item.of('minecraft:gold_block')
    )

    // Define and build the machine structure.
        structure
            .pattern(['XXX', 'XIX', 'XXX'])
            .pattern(['XMX', 'I I', 'XMX'])
            .pattern(['XXX', 'XCX', 'XXX'])
            .set('X',api.block('minecraft:bricks'))
            .set('M',api.patternEntry( // Use api.patternEntry to declare a modifier position.
                api.block('minecraft:blast_furnace'), // Matching predicate.
                [   // Modifiers allowed at this position.
                    diamondSpeedup,
                    goldDoubling
                ]
            ))
            .set('I',api.anyOf(api.block('mmcr:item_input_bus'),api.block('mmcr:item_output_bus'),api.block('mmcr:energy_input_hatch')))
            .controller('C')
            .build()

    // See server_scripts/recipe/A_Modifier_Machine.js for the recipe definitions.
})
