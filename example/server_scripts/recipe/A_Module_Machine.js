ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_space_reassembler',
        tick_time: 100,
        parallelized: true,
        required_host_ids: [
            "mmcr_kubejs:kubejs_space_elevator"
        ], // use required_host_ids to declare this recipe must happen in which machines
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

    // also host recipe is available
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
                    count: 12 // amazing uhh
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 100
            }
        ]
    })
    // you have finished your first machine again ! just launch game and see what happen
})
