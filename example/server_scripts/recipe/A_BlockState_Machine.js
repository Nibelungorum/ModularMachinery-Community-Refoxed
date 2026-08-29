ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_reactor',
        tick_time: 300,
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:apple',
                count: 3
            },
            {
                type: 'fluid',
                io: 'input',
                fluid: 'minecraft:water',
                amount: 1
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:diamond',
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

    // This recipe cannot run because the structure has no energy input port.
    // The requirement and recipe systems are intentionally independent.
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
