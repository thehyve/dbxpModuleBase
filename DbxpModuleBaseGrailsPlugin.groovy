class DbxpModuleBaseGrailsPlugin {
    // the plugin version
    def version = "0.4.19"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on are declared in BuildConfig.groovy
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
