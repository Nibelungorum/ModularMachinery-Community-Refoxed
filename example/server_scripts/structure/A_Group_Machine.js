MMCREvents.server(event => {
    const api = event.getAPI()
    const distillation_tower = event.createStructure("mmcr_kubejs:kubejs_distillation_tower")
    const eco_matrix = event.createStructure("mmcr_kubejs:kubejs_eco_matrix")

    distillation_tower
    (['  XXX  ','  AAA  ','       ','       '])
    ([' XXXXX ',' B   B ','  ACA  ','       '])
    (['XXXXXXX','A     A',' B   B ','  DDD  '])
    (['XXXXXXX','A     A',' B   B ','  DDD  '])
    (['XXXXXXX','A     A',' B   B ','  DDD  '])
    ([' XXXXX ',' B   B ','  BBB  ','       '])
    (['  XXX  ','  BEB  ','       ','       '])
    api.portRequirements({}), api.portTierRequirements(towerTiers), [], MachineStructureRequirements.EMPTY)

    (['  XXX  ','  AAA  ','       ','       ','       '])
    ([' XXXXX ',' B   B ','  ACA  ','  ACA  ','       '])
    (['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '])
    (['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '])
    (['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '])
    ([' XXXXX ',' B   B ','  BBB  ','  BBB  ','       '])
    (['  XXX  ','  BEB  ','       ','       ','       '])
    api.portRequirements({}), api.portTierRequirements(towerTiers), [], MachineStructureRequirements.EMPTY)

    (['  XXX  ','  AAA  ','       ','       ','       ','       '])
    ([' XXXXX ',' B   B ','  ACA  ','  ACA  ','  ACA  ','       '])
    (['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '])
    (['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '])
    (['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '])
    ([' XXXXX ',' B   B ','  BBB  ','  BBB  ','  BBB  ','       '])
    (['  XXX  ','  BEB  ','       ','       ','       ','       '])
    api.portRequirements({}), api.portTierRequirements(towerTiers), [], MachineStructureRequirements.EMPTY).build()

        // Do not forget this
        .build()

    // Move to server_scripts/recipe/A_Level_Machine.js see how to create some special recipes
})