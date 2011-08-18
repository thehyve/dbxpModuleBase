class DbxpModuleBaseGrailsPlugin {
    // the plugin version
    def version = "0.4.1"
	
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on
    def dependsOn = [
		jquery:"1.6.1.1 => *",
		jqueryDatatables: "1.7.5 => *",
		jqueryUi: "1.8.11 => *",
		famfamfam: "1.0.1",
		resources: "1.0 => *"
	]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/views/index.gsp",
            "grails-app/controllers/test"
    ]

    def author = "Robert Horlings and Siemen Sikkema"
    def authorEmail = ""
    def title = "Base for modules communicating with GSCF"
    def description = '''\\
This plugin provides all basic features common to all grails modules that communicate with the GSCF.
'''
}
