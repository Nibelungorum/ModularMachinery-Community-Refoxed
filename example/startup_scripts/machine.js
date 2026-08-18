// KubeJS recreation of the development defaults. It deliberately does not read mmcr:* definitions.
MMCREvents.startup(function(event) {
  var api = event.api()
  var LinkedHashMap = Java.loadClass('java.util.LinkedHashMap')
  var NS = 'mmcr_kubejs'

  function cloneId(path) {
    return NS + ':kubejs_' + path
  }

  function register(path, configure) {
    var builder = event.createMachine(cloneId(path))
      .displayNameKey('machine.mmcr.' + path)
      .recipeFamily(cloneId(path))
    configure(builder)
    builder.register()
  }

  function couplerPattern(count) {
    var blocks = new LinkedHashMap()
    for (var x = 0; x < count; x++) blocks.put(api.pos(x, 0, 0), api.coupler())
    return api.blockArray(blocks)
  }

  register('blast_furnace', function(builder) {
    return builder.allowMultithreading().allowParallelism().maxParallelAmount(2147483647)
  })
  register('alloy_furnace', function(builder) {
    return builder.appearance('minecraft:bricks').allowModifiers()
  })
  register('cracker', function(builder) {
    return builder.allowVerticalFacing().fullyRotationallySymmetric()
  })
  register('reactor', function(builder) {
    return builder.appearance('minecraft:blue_ice')
  })
  register('thermal_smelting_furnace', function(builder) {
    return builder.appearance('minecraft:smooth_basalt').allowMultithreading().allowParallelism().maxParallelAmount(2147483647)
  })
  register('purpur_furnace', function(builder) {
    return builder
      .appearance('minecraft:end_stone_bricks').allowMultithreading().allowParallelism().maxParallelAmount(4)
      .runningSound('minecraft:block.furnace.fire_crackle').finishSound('minecraft:entity.ender_dragon.growl')
      .smartInterface('Mode', 1, 3).priority(0).valueType('integer').end()
      .smartInterface('Temperature', 400, 6800).priority(1).valueType('integer').end()
      .smartInterface('ConversionRate', 0, 1).priority(2).valueType('float').end()
  })
  register('distillation_tower', function(builder) {
    return builder.appearance('minecraft:polished_blackstone').allowMultithreading().allowParallelism().maxParallelAmount(4)
      .expandableStructure(true)
  })
  register('eco_matrix', function(builder) {
    return builder.appearance('minecraft:sea_lantern').expandableStructure(true)
  })
  register('space_elevator', function(builder) {
    return builder
      .appearance('minecraft:smooth_quartz')
      .controllerBaseTexture('minecraft:block/quartz_block_bottom')
      .formedPortBaseTexture('minecraft:block/quartz_block_bottom')
      .host(cloneId('space_reassembler')).pattern(couplerPattern(3))
  })
  register('space_reassembler', function(builder) {
    return builder.appearance('minecraft:quartz_pillar').module().pattern(couplerPattern(1))
  })
})
