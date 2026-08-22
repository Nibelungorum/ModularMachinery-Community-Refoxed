ServerEvents.recipes( event => {

    // I don't think this machine have some things interesting
    // Just a recipe which can produce fluid uhh
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_cracker',
        tick_time: 300,
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:apple',
                count: 3
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot', // what a magic
                    count: 10
                }
            },
            {
                type: 'fluid', // just set to fluid
                io: 'output',
                stack: {
                    id: 'minecraft:water', // just be the identifier
                    amount: 1000 // mB, pay attention to "amount"
                }
            },
            {
                type: 'energy', // I think you know that, if you do not set energy input, there will not be any energy consume
                io: 'input',
                fe_per_tick: 20
            }
        ]
    })
    // I found that with no id is a thing which every 爽
})