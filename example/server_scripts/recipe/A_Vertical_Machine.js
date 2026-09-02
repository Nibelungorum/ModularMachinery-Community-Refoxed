ServerEvents.recipes( event => {

    // This example focuses on fluid output rather than recipe complexity.
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_cracker',
        tick_time: 300,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:apple',
                count: 3
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 10
                }
            },
            {
                type: 'minecraft:fluid', // Set the requirement type to fluid.
                io: 'output',
                stack: {
                    id: 'minecraft:water', // Fluid identifier.
                    amount: 1000 // Amount in mB.
                }
            },
            {
                type: 'neoforge:energy', // No energy is consumed when no energy input is defined.
                io: 'input',
                fe_per_tick: 20
            }
        ]
    })
    // Recipe IDs are optional in KubeJS.
})
