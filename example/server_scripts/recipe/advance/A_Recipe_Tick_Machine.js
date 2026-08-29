ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_recipe_ticker',
        tick_time: 300,
        // Define the item and energy requirements.
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:coal',
                count: 10000
            },
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:diamond',
                count: 8
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:gold_ingot',
                    count: 9
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 20
            }
        ]
    })
})