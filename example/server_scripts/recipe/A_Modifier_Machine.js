ServerEvents.recipes( event => {

    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_alloy_furnace', // Use the machine's registered identifier.
        tick_time: 300,
        parallelized: true,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: '#minecraft:swords', // Tags can be used as ingredients.
                components: { // Item data components can also be matched.
                    'minecraft:enchantments': {
                        'minecraft:sharpness': 2
                    }
                },
                count: 1,
                // consume_chance: 0.5 // Set the consumption chance; 0 keeps the input intact.
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 2,
                    components: { // Output data components are also supported.
                        'minecraft:custom_name': {
                            text: 'What an amazing design!'
                        },
                        'minecraft:enchantments': {
                            'minecraft:sharpness': 3
                        }
                    }
                },
                // chance: 0.5 // Outputs can have a chance as well.
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 10
            }
        ]
    })
    //.id('mmcr_kubejs:blast_furnace_1') Omit this call to leave the recipe ID automatic.

    // The modifier recipe is complete.
})
