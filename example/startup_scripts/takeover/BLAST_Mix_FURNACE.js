MMCREvents.startup(event => {
    // registry vanilla blast furnace block as workstation in recipe pool mmcr_kubejs:kubejs_blast_furnace
    event.addRecipePoolWorkstation(
        "mmcr_kubejs:kubejs_blast_furnace", // recipe pool id
        "minecraft:blast_furnace"
    )

    // add mmcr blast furnace controller as workstation in vanilla blast furnace recipe page
    event.addMachineWorkStation(
        "mmcr_kubejs:kubejs_blast_furnace", // machine id
        "minecraft:smelting"
    )
})