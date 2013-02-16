modules = {
	moduleBase {
		dependsOn 'jquery, jquery-ui, datatables'

		resource url:[plugin: 'dbxpModuleBase', dir:'js', file: 'topnav.js', disposition: 'head']
		resource url:[plugin: 'dbxpModuleBase', dir:'css', file: 'module.css', disposition: 'head']
	}

    datatables {
        dependsOn 'jquery-ui'

        resource url:[plugin: 'dbxpModuleBase', dir:'css', file: 'datatables.css', disposition: 'head']
        resource url:[plugin: 'jquery-datatables', dir:'js', file:'jquery.dataTables.min.js', disposition: 'head']
        resource url:[plugin: 'dbxpModuleBase', dir:'js', file: 'datatables.js', disposition: 'head']
    }

}