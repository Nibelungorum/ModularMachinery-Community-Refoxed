MMCREvents.startup(event => {
    event.createMachine('mmcr_kubejs:ae2lt')
        .allowParallelism()
        .maxParallelAmount(MMCR.getValues().INT_MAX / 2)
        .register()
})
