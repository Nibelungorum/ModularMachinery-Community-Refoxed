ServerEvents.recipes( event => {

    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_alloy_furnace', // do not forget your registration name
        tick_time: 10,
        parallelized: true,
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: '#minecraft:swords', // you are allowed to use tag
                components: { // you are allowed to use data components
                    'minecraft:enchantments': {
                        'minecraft:sharpness': 2
                    }
                },
                count: 1,
                consume_chance: 0.5 // you can set its consume chance, when it is 0, it will be never consumed
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 2,
                    components: { // here allowed data components too
                        'minecraft:custom_name': {
                            text: 'What an amazing design!'
                        },
                        'minecraft:enchantments': {
                            'minecraft:sharpness': 3
                        }
                    }
                },
                chance: 0.5 // output chance is also allowed
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 10
            }
        ]
    })
    //.id('mmcr_kubejs:blast_furnace_1') this way we do not register it's id

    // you have finished your first machine again ! just launch game and see what happen
})
