modules = {
	moduleBase {
		dependsOn 'jquery, jquery-ui, datatables'

		resource url:[plugin: 'dbxpModuleBase', dir:'js', file: 'topnav.js']
	}

    datatables {
        dependsOn 'jquery-ui'

        resource url:[plugin: 'dbxpModuleBase', dir:'css', file: 'datatables.css']
        resource url:[plugin: 'jquery-datatables', dir:'js', file:'jquery.dataTables.min.js']
        resource url:[plugin: 'dbxpModuleBase', dir:'js', file: 'datatables.js']
    }
}