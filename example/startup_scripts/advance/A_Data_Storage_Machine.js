MMCREvents.startup(event => {
    const machine = event
        .createMachine("mmcr_kubejs:kubejs_data_storage_machine")
        .displayNameKey("machine.mmcr_kubejs.kubejs_data_storage_machine")
        .recipeFamily("mmcr_kubejs:kubejs_data_storage_machine")
        .appearance("minecraft:crying_obsidian");

    // Some lib we will use
    // see what, big integer
    const BigInteger = Java.loadClass("java.math.BigInteger")
    const ReadableNumber = Java.loadClass("cn.howxu.mmcr.api.publicapi.ReadableNumber")
    const DataValue = Java.loadClass("cn.howxu.mmcr.api.data.DataValue")
    const api = MMCR.getAPI()
    const RecipeIO = api.recipeIO()
    const OutputPolicy = api.outputPolicy()

    const INT_MAX = MMCR.getValues().INT_MAX

    machine
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                var storage = ctx.dataStorage()
                if (storage == null) return

                // get big integer data from data storage
                var stored = BigInteger.ZERO
                var saved = storage.get("energy")

                if (saved.isPresent()) {
                    stored = saved.get()
                        .asBigInteger()
                        .orElse(BigInteger.ZERO)
                }

                // every 5 ticks do one input check
                if (ctx.isDue(5)) {
                    // because of the neoforge limit
                    // 2.1G is the biggest input and output value
                    var available = ctx.ioView().energyInput()
                    var maxRequest = Math.min(available, INT_MAX)

                    var low = 0
                    var high = maxRequest

                    // a binary search for every input hatch for transferLimit
                    while (low < high) {
                        var candidate = low + Math.ceil((high - low) / 2)

                        var probe = ctx.ioPlan()
                        probe.addInput(api.energyRequirement(RecipeIO.INPUT, candidate))

                        if (probe.simulate().energySatisfied()) {
                            low = candidate
                        } else {
                            high = candidate - 1
                        }
                    }

                    if (low > 0) {
                        var inputPlan = ctx.ioPlan()
                        inputPlan.addInput(api.energyRequirement(RecipeIO.INPUT, low))

                        var next = stored.add(BigInteger.valueOf(low))
                        var inputSimulation = inputPlan.simulate()

                        if (inputSimulation.energySatisfied() && inputPlan.commit(transaction => {
                            // update the data storage value
                            storage.set("energy", DataValue.of(next), transaction)
                        }).successful()) {
                            stored = next
                        }
                    }
                }

                // every 5 tick do one output
                if (ctx.isDue(5)) {

                    // get output capability
                    var outputCapacity = ctx.ioView().energyOutputCapacity()

                    if (outputCapacity > 0 && stored.signum() > 0) {
                        // limit 2.1G
                        var requestedBig = stored.min(
                            BigInteger.valueOf(Math.min(outputCapacity, INT_MAX))
                        )
                        var requested = requestedBig.intValue()

                        if (requested > 0) {
                            var outputPlan = ctx.ioPlan()

                            // some output hatches have transformer limit, so OutputPolicy.ALLOW_PARTIAL
                            outputPlan.addOutput(
                                api.energyRequirement(RecipeIO.OUTPUT, requested),
                                OutputPolicy.ALLOW_PARTIAL
                            )

                            var simulation = outputPlan.simulate()
                            var outputs = simulation.outputs()

                            if (!outputs.isEmpty()) {
                                var accepted = outputs.get(0).accepted()

                                if (accepted > 0) {
                                    var next = stored.subtract(
                                        BigInteger.valueOf(accepted)
                                    )

                                    // use js promise to update storage
                                    if (outputPlan.commit(transaction => {
                                        storage.set("energy", DataValue.of(next), transaction)
                                    }).successful()) {
                                        stored = next
                                    }
                                }
                            }
                        }
                    }
                }

                if (stored.signum() === 0){
                    ctx.screenText().append(
                        api.screenScope().OPERATION,
                        api.id("mmcr_kubejs:fe_storage_status"),
                        Text.literal("No FE stored.")
                    )
                    return
                }
                ctx.screenText().append(
                    api.screenScope().OPERATION,
                    api.id("mmcr_kubejs:fe_storage_status"),
                    Text.literal("FE stored: " + ReadableNumber.formatCompact(stored))
                )

            })
        )

    machine.register()

})
