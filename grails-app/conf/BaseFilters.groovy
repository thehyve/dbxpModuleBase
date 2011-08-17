import javax.servlet.http.HttpServletResponse
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.dbxp.moduleBase.AuthenticationRequired
import org.dbxp.moduleBase.NoAuthenticationRequired
import org.dbxp.moduleBase.RefreshUserInformation
import org.dbxp.moduleBase.Study

/**
 * This class controls for which parts of the module authentication is required.
 * This can be configured in three ways. By default, access to URIs (except
 * simple views without a controller) requires authentication. This can be
 * changed by setting <code>module.defaultAuthenticationRequired = false</code>
 * in Config.groovy.
 * Finer control can be applied using the annotations
 * <code>AuthenticationRequired</code> and <code>NoAuthenticationRequired</code>
 * . These can be applied to controllers and/or actions. The former takes
 * precedence (in case both are applied). If <code>AuthenticationRequired</code>
 * is absent, the following applies. We don't require authentication if:
 * - The <code>AuthenticationRequired</code> annotation is present, or ...
 * - Neither annotation is present and <code>module.defaultAuthenticationRequired = false</code>
 *
 * The following URLs serve as tests (smiley face indicates: no authentication)
 *
 * http://localhost:8080/dbxpModuleBase/filterTestNoAnnotation/actionAuthenticationRequired                  -> :(
 * http://localhost:8080/dbxpModuleBase/filterTestNoAnnotation/actionNoAuthenticationRequired                -> :)
 * http://localhost:8080/dbxpModuleBase/filterTestNoAnnotation/actionNoAnnotation                            -> depends on configuration
 *
 * http://localhost:8080/dbxpModuleBase/filterTestNoAuthenticationRequired/actionAuthenticationRequired      -> :(
 * http://localhost:8080/dbxpModuleBase/filterTestNoAuthenticationRequired/actionNoAuthenticationRequired    -> :)
 * http://localhost:8080/dbxpModuleBase/filterTestNoAuthenticationRequired/actionNoAnnotation                -> :)
 *
 * http://localhost:8080/dbxpModuleBase/filterTestAuthenticationRequired/actionAuthenticationRequired        -> :(
 * http://localhost:8080/dbxpModuleBase/filterTestAuthenticationRequired/actionNoAuthenticationRequired      -> :)
 * http://localhost:8080/dbxpModuleBase/filterTestAuthenticationRequired/actionNoAnnotation                  -> :(
 *
 * TODO: write doc on gscf authentication procedure
 */
class BaseFilters {
    def gscfService
    def authenticationService
	def synchronizationService

	// UrlMappingsHolder is used for determining default controller.
	def grailsUrlMappingsHolder

    def filters = {
        userCheck(controller: '*', action: '*') {

            before = {
				// If no controller is given, try to find the default controller from url mappings.
				// If might be that no controllerName could be found. In that case, the isAuthenticationRequired
				// method will return the configuration value
//				if( !controllerName )
//					controllerName = grailsUrlMappingsHolder.match( "/" + grailsApplication.metadata['app.name'] )?.getControllerName();

				// allow calls to rest controller
                if (controllerName == 'rest') {
                    return true
                }
				
				// From here on, we require authentication. So if that fails,
				// we'll return false.
				
				// In order to authenticate, we need the location of GSCF
				if (!ConfigurationHolder.config.gscf.baseURL) {
					throw new Exception("No GSCF instance specified. Please check configuration and specify GSCF location by setting gscf.baseURL to the URL (without trailing '/') of the correct GSCF instance in your configuration file.")
				}

				if (!ConfigurationHolder.config.module.consumerId) {
					throw new Exception("No module consumer Id specified. Please check configuration and specify the consumer id by setting module.consumerId to the URL of the module.")
				}
				
				if( !isAuthenticationRequired( grailsApplication, controllerName, actionName ) ) {
					log.trace "No authentication required: " + controllerName + " - " + actionName
					
					// We do want to check who is logged in, even when no authentication is required if annotation
					// UserRefreshRequired is added to the controller or action
					if( userRefreshRequired( grailsApplication, controllerName, actionName ) ) {
						def loggedIn = authenticationService.checkLogin( request.method, params );

						if (!loggedIn.status) {
							// Set the flag loggingIn to true, so the system can synchronize after logging in
							// See also synchronizeAuthorization Filter
							session.loggingIn = true;
		
							// Sent to the silent redirect
							redirect( url: loggedIn.redirect + "&silent=true" )
							return false
						}
					}
					
					return true
				} else {
					log.trace "Authentication required: " + controllerName + " - " + actionName

					//
					def loggedIn = authenticationService.checkLogin( request.method, params )
										
					if (!loggedIn.status) {
						// Set the flag loggingIn to true, so the system can synchronize after logging in
						// See also synchronizeAuthorization Filter
						session.loggingIn = true;
	
						redirect(url: loggedIn.redirect)
						return false
					}
				}

                return true
            }
        }
		
        restUserCheck(controller: 'rest', action: '*') {
            before = {
				if( !isAuthenticationRequired(grailsApplication, controllerName, actionName) )
					return true
				
                // We are handling the Rest controller as a special case. The rest calls are made
                // outside of the users browser session, so it should always contain a session token.
                // Moreover, it should never redirect the user, but instead send a 403 error
                if (!params.sessionToken && !session.sessionToken) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST, "No sessiontoken given")
                    render "No sessiontoken given"
                    return false
                }

                // The sessiontoken might be injected using params
				if (params.sessionToken)
                    session.sessionToken = params.sessionToken

                try {
                    def user = gscfService.getUser(session.sessionToken)

                    if (user) {
                        // Locate user in database or create a new user
                        authenticationService.findOrUpdateUser(user)
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED, "No user logged in")
                        render "No user logged in"
                        return false
                    }
                } catch (Exception e) {
                    log.error("Unable to fetch user from GSCF", e)
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST, "Unable to fetch user from GSCF")
                    render "Unable to fetch user from GSCF"
                    return false
                }

                return true
            }
        }
		
		synchronizeAuthorization( controller: '*', action: '*' ) {
			before = {
				// Check whether the authorization should be synchronized
				if( session.loggingIn ) {
					// Reset the flag so the synchronization will only be performed once
					session.loggingIn = false;
					
					// Perform synchronization of authorization for all studies
					try {
						synchronizationService.initSynchronization( session.sessionToken, session.user )

						// First synchronize all studies that have been changed
						def changedStudies = synchronizationService.synchronizeChangedStudies();

						def unchangedStudies 
						if( changedStudies )
						 	unchangedStudies = Study.findAll( "FROM Study s WHERE s NOT IN (:changedStudies)", [ "changedStudies": changedStudies ] );
						else
							unchangedStudies = Study.list()
							
						unchangedStudies.each { study ->
							log.info "Synchronize authorization for " + study + " with user " + session.user
							synchronizationService.synchronizeAuthorization( study )
						}
					} catch( Exception e ) {
						log.error( "An exception occurred during synchronization: " + e.getMessage() )
						e.printStackTrace()
						
						// Continue even when authorization is not correctly synchronized
					}
				}
				
				return true
			}
		}
    }
	
	protected boolean isAuthenticationRequired( def grailsApplication, String controllerName, String actionName ) {
		// Check the configuration for authentication requirement
		// Defaults to true (because we use getMandatory)
		// This method is able to handle: "true", "false", true, false and 
		// Default (when value == null) to 'true'
		boolean configurationAuthenticationRequired = true
		try {
			configurationAuthenticationRequired = ConfigurationHolder.config.module.getMandatory( 'defaultAuthenticationRequired' ) as Boolean;
		} catch( Exception e ) {
			// An exception occurs if the configuration option is not set. We can ignore it, since the value is set
			// to true by default
		}
		 
		// If no controllerName is given, return configuration value
		if( !controllerName )
			return configurationAuthenticationRequired
		
		// Get instances of the controller class and action to be able
		// to acquire the authentication annotations.
		// See http://www.mengu.net/post/annotating-your-grails-controller-classes-and-actions
		def controllerClass     = grailsApplication.controllerClasses.find { it.name == controllerName.capitalize() }

		// if no action is specified, use the default instead
		def mutableActionName   = actionName ?: controllerClass.defaultActionName
		def controllerClazz     = controllerClass.clazz

		def controllerAction    = controllerClazz.declaredFields.find { mutableActionName == it.name.toString() }

		// if no is action found, return true to show a standard 404
		if (!controllerAction) return true

		// Determine whether the annotations tell us to authenticate the user for this action
		boolean annotationAuthenticationRequired =
			controllerAction.isAnnotationPresent(AuthenticationRequired) ||
			(controllerClazz.isAnnotationPresent(AuthenticationRequired) &&
			!controllerAction.isAnnotationPresent(NoAuthenticationRequired))

		boolean annotationNoAuthenticationRequired =
			controllerAction.isAnnotationPresent(NoAuthenticationRequired) ||
			(controllerClazz.isAnnotationPresent(NoAuthenticationRequired) &&
			!controllerAction.isAnnotationPresent(AuthenticationRequired))

		// We always require authentication if annotationAuthenticationRequired == true
		// else, we don't require authentication if:
		//  - annotation explicitly says we don't need it
		//  - there are no annotations and the configuration doesn't
		//    overrule
		if (!annotationAuthenticationRequired) {
			if (annotationNoAuthenticationRequired) {
				return false
			} else if (!configurationAuthenticationRequired) {
				return false
			}
		}
		
		return true
	}
	
	protected boolean userRefreshRequired( def grailsApplication, String controllerName, String actionName ) {
		// This functionality has not been implemented correctly, so never refresh user information.
		return false;
		
		// If no controllerName is given, return configuration value
		if( !controllerName )
			return false;
		
		// Get instances of the controller class and action to be able
		// to acquire the authentication annotations.
		// See http://www.mengu.net/post/annotating-your-grails-controller-classes-and-actions
		def controllerClass     = grailsApplication.controllerClasses.find { it.name == controllerName.capitalize() }

		// if no action is specified, use the default instead
		def mutableActionName   = actionName ?: controllerClass.defaultActionName
		def controllerClazz     = controllerClass.clazz

		def controllerAction    = controllerClazz.declaredFields.find { mutableActionName == it.name.toString() }

		// if no is action found, return true to show a standard 404
		if (!controllerAction) 
			return false
		
		// Determine whether the annotations tell us to authenticate the user for this action
		boolean annotationAuthenticationRequired =
			controllerAction.isAnnotationPresent(RefreshUserInformation) ||
			controllerClazz.isAnnotationPresent(RefreshUserInformation)
	}
}

