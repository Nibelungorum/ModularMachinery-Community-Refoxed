MMCREvents.server(event => {
    const api = event.getAPI()

    // This way we use a new formation, we can make the builder as a value, the use it for some advanced features
    // Typically, in A_Simple_Machine you can also do like this
    const structure = event.createStructure("mmcr_kubejs:kubejs_alloy_furnace")

    // This is a modifier creater, with the way of object and function api.singleBlockModifier()
    // This declares the diamond block as one special block
    const diamondSpeedup = api.singleBlockModifier(
        'alloy_furnace_diamond_speedup', // this is the identifier, for some runtime usage
        api.block('minecraft:diamond_block'), // api.block() are optional, you can use any predicate
        // this is an array argument, meaning you can make the modifier a lot of functions
        [
            api.modifier(
                'duration', // this modifier block will change the
                'input',    // because duration happens before the recipe start, so you should use input as sign
                0.5,        // the value
                'multiply', // the value calculation way
                false       // will it affect the output chance? If it's no, it will modify the output counts
            )
        ],
        Item.of('minecraft:diamond_block') // When you use JEI 3D preview, this will append an info on diamond block in JEI
    )

    // one another modifier, will make the output x2
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

    // The build our machine structure
        structure
            .pattern(['XXX', 'XIX', 'XXX'])
            .pattern(['XMX', 'I I', 'XMX'])
            .pattern(['XXX', 'XCX', 'XXX'])
            .set('X',api.block('minecraft:bricks'))
            .set('M',api.patternEntry( // use api.patternEntry to create a special modifier declaration
                api.block('minecraft:blast_furnace'), // predicate
                [   // this array contains all modifiers
                    diamondSpeedup,
                    goldDoubling
                ]
            ))
            .set('I',api.anyOf(api.block('mmcr:item_input_bus'),api.block('mmcr:item_output_bus'),api.block('mmcr:energy_input_hatch')))
            .set('C',api.block('mmcr:kubejs_alloy_furnace_controller'))
            .build()

    // Move to server_scripts/recipe/A_Modifier_Machine.js see how to create some special recipes
})