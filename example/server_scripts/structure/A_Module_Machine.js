// Module couplers belong to the machine structure, not the startup machine registration.

MMCREvents.server(event => {
    const api = event.getAPI()

    event.createStructure("mmcr_kubejs:kubejs_space_elevator")
        .pattern("XXX")
        .set("X", api.coupler())
        .build()

    event.createStructure("mmcr_kubejs:kubejs_space_reassembler")
        .pattern("X")
        .set("X", api.coupler())
        .build()
})
