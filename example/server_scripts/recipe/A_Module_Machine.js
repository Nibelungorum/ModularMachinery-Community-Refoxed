ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_space_reassembler',
        tick_time: 100,
        parallelized: true,
        required_host_ids: [
            "mmcr_kubejs:kubejs_space_elevator"
        ], // Declare the host machines where this recipe can run.
        requirements: [
            {
                type: 'item',
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
                type: 'item',
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
                type: 'energy',
                io: 'input',
                fe_per_tick: 100
            }
        ]
    })

    // Recipes can also run on the host machine.
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_space_elevator',
        tick_time: 1000,
        parallelized: true,
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:apple',
                count: 1
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:golden_apple',
                    count: 12
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 100
            }
        ]
    })
    // The module and host recipes are complete.
})
