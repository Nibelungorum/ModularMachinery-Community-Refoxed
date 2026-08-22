ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_reactor',
        tick_time: 300,
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:apple', // unbelievable apple generator
                count: 3
            },
            {
                type: 'fluid',
                io: 'input',
                fluid: 'minecraft:water',
                amount: 1000
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:diamond', // what a magic
                    count: 10
                }
            },
            {
                type: 'fluid',
                io: 'output',
                stack: {
                    id: 'minecraft:lava',
                    amount: 250
                }
            },
            {
                type: 'energy',
                io: 'output',
                fe_per_tick: 200
            }
        ]
    })

    // and this is an error recipe
    // with no energy input port in structure, you can't process this recipe
    // sure, requirement system and recipe system do not have close connection
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_reactor',
        tick_time: 200,
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:golden_apple',
                count: 2
            },
            {
                type: 'fluid',
                io: 'input',
                fluid: 'minecraft:water',
                amount: 800
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:gold_ingot',
                    count: 2
                }
            },
            {
                type: 'fluid',
                io: 'output',
                stack: {
                    id: 'minecraft:lava',
                    amount: 450
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 10
            },
            {
                type: 'energy',
                io: 'output',
                fe_per_tick: 200
            }
        ]
    })
})