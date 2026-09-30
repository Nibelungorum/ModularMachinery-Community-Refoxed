ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:kubejs_space_reassembler',
        tick_time: 100,
        parallelized: true,
        required_host_ids: [
            "mmcr_kubejs:kubejs_space_elevator"
        ], // Declare the host machines where this recipe can run.
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:potion',
                components: {
                    'minecraft:potion_contents': {
                        potion: 'minecraft:water'
                    }
                },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:potion',
                    count: 1,
                    components: {
                        'minecraft:potion_contents': {
                            potion: 'minecraft:healing'
                        }
                    }
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 100
            }
        ]
    })

    // Recipes can also run on the host machine.
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:kubejs_space_elevator',
        tick_time: 1000,
        parallelized: true,
        required_host_ids: [
            "mmcr_kubejs:kubejs_space_elevator"
        ],
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:apple',
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:golden_apple',
                    count: 12
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 100
            }
        ]
    })

    // One another recipe pool for space_reassembler
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:kubejs_space_miner',
        tick_time: 200,
        parallelized: true,
        required_host_ids: [
            "mmcr_kubejs:kubejs_space_elevator"
        ],
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:apple',
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 12
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 100
            }
        ]
    })


    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:kubejs_space_miner',
        tick_time: 100,
        required_host_ids: [
            "mmcr_kubejs:kubejs_space_elevator"
        ],
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:iron_ingot',
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:gold_nugget',
                    count: 10
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: MMCR.getValues().INT_MAX * 2
            }
        ]
    })

    // The module and host recipes are complete.
})
