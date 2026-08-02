const demoCube = new MMCR_MACHINE_BUILDER('mmcr:demo_cube')
    .localizedName('Demo Cube')
    .pattern(`
        CCC
        CFC
        CCC
    `, { C: 'mmcr:casing', F: 'mmcr:controller' });

demoCube.register();
