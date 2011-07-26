modules = {
	moduleBase {
		dependsOn 'jquery, jquery-ui'

		resource id:'css', url:[plugin: 'dbxpModuleBase', dir:'css', file: 'datatables.css']
//		resource id:'css', url:[plugin: 'dbxpModuleBase', dir:'css/cupertino', file: 'jquery-ui-1.8.13.custom.css']
		resource id:'js', url:[plugin: 'dbxpModuleBase', dir:'js', file: 'topnav.js']
		resource id:'js', url:[plugin: 'dbxpModuleBase', dir:'js', file: 'datatables.js']
	}
}