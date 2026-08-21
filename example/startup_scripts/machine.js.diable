// KubeJS Start up Event Register Machine Definations
// This stage will registe a controller block for your machine

// First, Listen the MMCREvents.startup
MMCREvents.startup(event => {
  // you can get api by this way, then you are able to access build-in public api
  var api = event.getAPI()
  // this is a namespace
  var NS = 'mmcr_kubejs'
  // this will make a new Identifier
  function cloneId(path) {
    return NS + ':kubejs_' + path
  }

  var LinkedHashMap = Java.loadClass('java.util.LinkedHashMap')

  var thermalCoilType = cloneId('thermal_smelting_coil')
  event.createLevelType(thermalCoilType).displayName('KubeJS Thermal Smelting Coil').register()
  function thermalCoil(path, priority, block, durationMultiplier) {
    event.createLevel(cloneId('thermal_smelting_coil_' + path))
      .type(thermalCoilType).priority(priority).state(block)
      .modifier({ durationMultiplier: durationMultiplier }).register()
  }
  thermalCoil('copper', 0, 'minecraft:copper_block', 0.9)
  thermalCoil('iron', 1, 'minecraft:iron_block', 0.8)
  thermalCoil('gold', 2, 'minecraft:gold_block', 0.7)
  thermalCoil('diamond', 3, 'minecraft:diamond_block', 0.6)

  function register(path, configure) {
    var builder = event.createMachine(cloneId(path))
      .displayNameKey('machine.mmcr_kubejs.' + path)
      .recipeFamily(cloneId(path))
    configure(builder)
    builder.register()
  }

  function couplerPattern(count) {
    var blocks = new LinkedHashMap()
    for (var x = 0; x < count; x++) blocks.put(api.pos(x, 0, 0), api.coupler())
    return api.blockArray(blocks)
  }
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
      .smartInterface('Mode_KJS', 1, 3).priority(0).valueType('integer').end()
      .smartInterface('Temperature_KJS', 400, 6800).priority(1).valueType('integer').end()
      .smartInterface('ConversionRate_KJS', 0, 1).priority(2).valueType('float').end()
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
