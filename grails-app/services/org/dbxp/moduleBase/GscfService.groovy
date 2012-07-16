package org.dbxp.moduleBase

import grails.converters.JSON
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.web.json.JSONArray
import org.springframework.web.context.request.RequestContextHolder

/**
 * EDP (External Data Provider) for GSCF
 *
 * @author Robert Horlings (robert@isdat.nl)
 * @version 0.9
 */
class GscfService implements Serializable {

	// No methods in this class change anything in the SAM database, so they don't have to be transactional
	static transactional = false

	/**
	 * Concatenates values from a map of key/value pairs to a URL request string
	 * @param params
	 * @return String
	 */
	String paramsMapToURLRequestString(params) {
		params.collect { param ->
			// If a Collection is given as value, the parameter should show up multiple times
			if (param.value instanceof Collection)
				param.value.collect { "$param.key=$it" }
			else "$param.key=$param.value"
		}.flatten().join("&")
	}

	/**
	 * Creates the URL for the current page (including GET parameters )
	 * @param params	HTTP request parameters for the action called
	 * @param appendParameters	Boolean to set whether request parameters should be added to the URL. 
	 * 							If set to true with a POST request, an exception will occur
	 * @return			String with URL for the current request 
	 */
	String paramsMapToURL( params, appendParameters = true ) {
		// get http request
		def request = RequestContextHolder.requestAttributes.request

		// build up return URL
		def returnUrl = ConfigurationHolder.config.grails.serverURL

		// replace contextPath with forwardURI
		// eg. /metabolomicsModule --> /metabolomicsModule/complete/path
		if (request.properties.contextPath != request.properties.forwardURI) {
			returnUrl = returnUrl.replace(request.properties.contextPath, request.properties.forwardURI)
		}

		// check if this is a GET request and parameters are added
		if (request.method != "GET" && (request.properties.queryString || (params && appendParameters))) {
			// Only GET parameters can be sent to the user. The calling method can call the method again with the appendParameters
			// property set to false or without a parameters object.
			throw new Exception("Parameters can only be added in GET requests")
		}

		// append request parameters
		if (request.properties.queryString) {
			// append queryString to returnURL
			returnUrl += "?${request.properties.queryString}"
		}

		// have we got params?
		if (params) {
			// remove controller and action from params if they exist as legacy
			// code might still add these.
			// Building a returnUrl based on controller and action sounds great,
			// but causes problems with client applications which use relative
			// paths. Especially when the urlmapping for "/" points to a controller
			// and action, the returnUrl will contain these as well breaking any
			// code that uses relative paths. Using the get request itself does not
			// create this problem.
			params.remove('action')
			params.remove('controller')
		}

		// append params argument to returnUrl
		if (appendParameters && params && params.size() > 0) {
			returnUrl += ((request.properties.queryString) ? "&" : "?") + paramsMapToURLRequestString(params)
		}

		// return the constructed return url
		return returnUrl
	}

	/**
	 * Returns the URL to let the user login at GSCF
	 *
	 * @param params 			Parameters of the action called
	 * @param token				Session token
	 * @param appendParameters	Boolean to set whether request parameters should be added to the URL. With a 
	 * 							POST request, the parameters will not be sent  
	 * 							If set to true with a POST request, an exception will occur
	 * @return URL to redirect the user to
	 */
	public String urlAuthRemote(params, token, appendParameters = true) {
		def redirectURL = ConfigurationHolder.config.gscf.baseURL + "/login/auth_remote?moduleURL=${moduleURL()}&"
		
		if( token )
			redirectURL += "consumer=${consumerId()}&token=$token&"
			
		// If the request is made using POST, we can't append parameters
		// in that case, the exception should be could, and we will create a URL
		// without appending the params
		def returnUrl
		
		// get http request
		def request = RequestContextHolder.requestAttributes.request

		if( request.method == "GET" ) {
			returnUrl = paramsMapToURL( params, appendParameters );
		} else {
			request.properties.queryString = "";
			returnUrl = paramsMapToURL( params, false );
		}

		redirectURL + 'returnUrl=' + returnUrl.encodeAsURL()
	}

	/**
	 * Returns the URL to let the user login at GSCF
	 *
	 * @param params Parameters of the action called
	 * @param token Session token
	 * @param appendReturnUrl	Boolean to set whether the return URL for the logout action will be sent to GSCF.
	 * @param appendParameters	Boolean to set whether request parameters should be added to the URL. This has no
	 * 							effect if appendReturnUrl is set to false
	 * 							If set to true with a POST request, an exception will occur
	 * @return URL to redirect the user to
	 */
	public String urlLogoutRemote(params, token, appendReturnUrl = false, appendParameters = true ) {
		def redirectURL = ConfigurationHolder.config.gscf.baseURL + "/logout/remote?moduleURL=${moduleURL()}&"

		if( token )
			redirectURL += "consumer=${consumerId()}&token=$token&"

		if( appendReturnUrl ) {
			def returnUrl = paramsMapToURL( params, appendParameters );

			redirectURL += '&returnUrl=' + returnUrl.encodeAsURL()
		}
		
		return redirectURL
	}


	/**
	 * Transform generic exceptions from callGSCF to exceptions with a bit more
	 * context to simplify the life of system administrators.
	 *
	 * @param sessionToken	Session token used for synchronization
	 * @param className String Name of the class (e.g. 'study' or 'assay')
	 * @param token String Token used for callGSCF
	 * @param e Exception The exception that was thrown
	 */
	void handleGSCFExceptions(sessionToken, className, token, Throwable e) {

		def tokenString = 'token'
		def instanceString = 'instance'
		if (token instanceof Collection) {
			tokenString += 's'
			instanceString += 's'
		}

		switch (e.class) {
			case NotAuthenticatedException:
				// If a NotAuthenticatedException occurs, GSCF is not ready yet for
				// returning public studies
				if( !sessionToken ) {
					throw new NotAuthenticatedException( "We have tried to synchronize data without a sessiontoken. This is only supported by GSCF versions > 0.8.5 (r1968). Update your GSCF version or disable synchronization." );
				} else {
					throw e;
				}
			case NotAuthorizedException:
				throw new NotAuthorizedException("User is not authorized to access $className with $tokenString: $token")
			case ResourceNotFoundException:
				throw new ResourceNotFoundException("No $instanceString of $className with $tokenString: $token found in GSCF")
			default:
			// No way to know how to transform other exceptions: re-throw it
				throw e
		}
	}

	/**
	 * Checks whether a user is logged in into GSCF
	 *
	 * @param sessionToken String
	 *
	 * @return boolean    True if the user is authenticated with GSCF, false otherwise
	 */
	boolean isUserLoggedIn(String sessionToken) {
		// Without session token, the user is never logged in
		if( !sessionToken )
			return false;
			
		try {
			callGSCF(sessionToken, "isUser")[0]['authenticated'] as boolean
		} catch (Exception e) {
			e.printStackTrace()
			false
		}
	}

	/**
	 * Retrieves the currently logged in user from GSCF
	 *
	 * @param sessionToken String
	 *
	 * @return HashMap
	 */
	HashMap getUser(String sessionToken) {
		try {
			def response = callGSCF(sessionToken, "getUser")

			// Add isAdministrator: false as default, in order to be able to
			// handle old versions of GSCF that don't provide this information
			if (!response.isAdministrator)
				response += [isAdministrator: false]

			response

		} catch (Exception e) {
			e.printStackTrace()
			return [:]
		}
	}

	/**
	 * Retrieve all allowed studies from the GSCF
	 *
	 * @param sessionToken String
	 *
	 * @return ArrayList
	 */
	ArrayList getStudies(String sessionToken) {
		try { 
			return callGSCF(sessionToken, "getStudies")
		} catch( e ) {
			handleGSCFExceptions(sessionToken, 'studies', [], e)
			return []
		}
	}

	/**
	 * Retrieve a list of studies from the GSCF
	 *
	 * @param sessionToken String
	 * @param studyTokens ArrayList
	 *
	 * @return ArrayList
	 */
	ArrayList getStudies(String sessionToken, ArrayList studyTokens) {
		try {
			callGSCF(sessionToken, "getStudies", ["studyToken": studyTokens])
		} catch (e) {
			handleGSCFExceptions(sessionToken, 'studies', studyTokens, e)
			return []
		}
	}

	/**
	 * Retrieve a single study from the GSCF
	 *
	 * @param sessionToken String
	 * @param study Study
	 *
	 * @return HashMap
	 */
	HashMap getStudy(String sessionToken, String studyToken) {
		try {
			getStudies(sessionToken, [studyToken])?.getAt(0)
		} catch( e ) {
			handleGSCFExceptions(sessionToken, 'studies', [studyToken], e)
			return []
		}
	}

	/**
	 * Retrieve all assays from a study from the GSCF
	 *
	 * @param sessionToken String
	 * @param study Study
	 *
	 * @return ArrayList
	 */
	ArrayList getAssays(String sessionToken, String studyToken) {
		try {
			callGSCF(sessionToken, "getAssays", ["studyToken": studyToken])
		} catch (e) {
			handleGSCFExceptions( sessionToken, 'study', studyToken, e)
			return []
		}
	}

	/**
	 * Retrieve selected assays from a study from the GSCF
	 *
	 * @param sessionToken String
	 * @param study Study
	 * @param assayTokens ArrayList
	 *
	 * @return ArrayList
	 */
	ArrayList getAssays(String sessionToken, String studyToken, ArrayList assayTokens) {
		try {
			callGSCF(sessionToken, "getAssays", ["studyToken": studyToken, 'assayToken': assayTokens])
		} catch (e) {
			// TODO: how to detect whether study or assay could not be found?
			handleGSCFExceptions(sessionToken, 'study', studyToken, e)
			return []
		}
	}

	/**
	 * Retrieve a single Assay from the GSCF
	 *
	 * @param sessionToken String
	 * @param study Study
	 * @param assay Assay
	 *
	 * @return HashMap
	 */
	HashMap getAssay(String sessionToken, String studyToken, String assayToken) {
		try {
			getAssays(sessionToken, studyToken, [assayToken])?.getAt(0) as HashMap
		} catch(e) {
			handleGSCFExceptions(sessionToken, 'assay', assayToken, e)
			return []
		}
	}

	/**
	 * Retrieve all sample from an assay from the GSCF
	 *
	 * @param sessionToken String
	 * @param assay Assay (parameter assay is optional, only used when you want to limit the list of samples of an Assay within a Study)
	 *
	 * @return ArrayList
	 */
	ArrayList getSamples(String sessionToken, String assayToken) {
		try {
			callGSCF(sessionToken, "getSamples", ["assayToken": assayToken])
		} catch (e) {
			handleGSCFExceptions( sessionToken, 'assay', assayToken, e)
			[]
		}
	}

	/**
	 * Retrieve a list of samples from the GSCF
	 *
	 * @param sessionToken String
	 * @param sampleTokens List of sampleTokens
	 *
	 * @return ArrayList
	 */
	ArrayList getSamples(String sessionToken, List sampleTokens) {
		try {
			callGSCF(sessionToken, "getSamples", ["sampleToken": sampleTokens])
		} catch (e) {
			handleGSCFExceptions( sessionToken, 'sample', sampleTokens, e)
		}
	}

	/**
	 * Retrieve a single Sample from the GSCF
	 *
	 * @param sessionToken String
	 * @param assayToken String
	 * @param sampleToken String
	 *
	 * @return HashMap
	 */
	HashMap getSample(String sessionToken, String assayToken, String sampleToken) {
		try {
			getSamples(sessionToken, [sampleToken])?.getAt(0) as HashMap
		} catch( e ) {
			handleGSCFExceptions( sessionToken, 'sample', sampleToken, e)
		}
	}

	/**
	 * Retrieve study access
	 *
	 * @param sessionToken String
	 * @param study Study
	 *
	 * @return ArrayList
	 */
	def getAuthorizationLevel(String sessionToken, String studyToken) {
		def mapResult = [:]
		def result
		
		try {
			result = callGSCF(sessionToken, "getAuthorizationLevel", ["studyToken": studyToken])[0]
		} catch( e ) {
			handleGSCFExceptions( sessionToken, "study", studyToken, e )
		}
		
		result.each {
			mapResult[ it.key ] = it.value
		}

		return mapResult;
	}

	/**
	 * Retrieve study version number
	 *
	 * @param sessionToken String
	 * @param study Study
	 *
	 * @return ArrayList
	 */
	def getStudyVersion(String sessionToken, String studyToken) {
		def result

		// Retrieve the version number from GSCF
		// If an exception occurs, just throw it, it should be handled in the calling method
		try {
			result = callGSCF(sessionToken, "getStudyVersion", ["studyToken": studyToken])[0]
		} catch( e ) {
			handleGSCFExceptions( sessionToken, "study", studyToken, e )
		}

		// Determine the version number from the system
		for( element in result ) {
			if( element.key == "version" )
				return Integer.valueOf( element.value )
		}

		return null;
	}

	/**
	 * Retrieve study version number for all studies the user has access to
	 *
	 * @param sessionToken String
	 *
	 * @return ArrayList
	 */
	def getStudyVersions(String sessionToken) {
		// Retrieve the version number from GSCF
		// If an exception occurs, just throw it, it should be handled in the calling method
		try {
			return callGSCF(sessionToken, "getStudyVersions")
		} catch( e ) {
			handleGSCFExceptions( sessionToken, "", "", e )
		}
	}

	/**
	 * Call GSCF Service via a secure call
	 *
	 * @param sessionToken Session token for connection to GSCF
	 * @param restMethod Method to call on GSCF rest controller
	 * @param restParams Parameters to provide to the GSCF rest method
	 * @param requestMethod Request method to retrieve data
	 *
	 * @return ArrayList
	 */
	ArrayList callGSCF(String sessionToken, String restMethod, HashMap restParams = [:], String requestMethod = "GET") {

		// Create a string of arguments to send to GSCF
		def args = "moduleURL=${moduleURL()}&" 
		
		if( sessionToken )
			args += "consumer=${consumerId()}&token=$sessionToken&"
		
		args +=	paramsMapToURLRequestString(restParams)

		// construct GSCF address
		def addr = "${restURL()}/${restMethod}"
		def connection

		// If the data to be sent is longer than +/- 2000 characters, the GET
		// method will fail, so we switch to POST if needed
		if (addr.size() + args.size() > 2000 && requestMethod != "POST") {
			log.warn "Calling $addr with request method POST instead of $requestMethod because content length is too long:" + (addr.size() + args.size())
			requestMethod = "POST"
		}

		// Call GSCF, depending on the requestMethod that is asked for
		try {
			log.info("GSCF REST-CALL ($requestMethod): $addr")
			log.info("GSCF REST-CALL args: " + args.toString() )

			switch (requestMethod.toUpperCase()) {
				case "GET":
					def url = addr + '?' + args
					connection = url.toURL().openConnection()

					break
				case "POST":
					connection = addr.toURL().openConnection()
					connection.setRequestMethod("POST")
					connection.doOutput = true

					def writer = new OutputStreamWriter(connection.outputStream)
					writer.write(args)
					writer.flush()
					writer.close()

					connection.connect()

					break
				default:
					throw new Exception("Unknown request method given. Use GET or POST")
			}

		} catch (Exception e) {
			log.error("GSCF Call failed when calling service: ${addr}", e)
			throw new Exception("Calling GSCF Rest method ${restMethod} failed. Please check log for more information.", e)
		}

		// Handle the response given by GSCF
		def gscfResponse = [:]

		// Handle the response given by GSCF
		switch (connection.responseCode) {
			case 400:    // Bad request
				throw new BadRequestException("Bad request made to GSCF server: $addr")
				break
			case 401:    // Not allowed to access this resource
				throw new NotAuthorizedException("User is not authorized to access the resource: $addr")
				break
			case 403:    // Incorrect authentication
				// The user is logged in to the module, but not to GSCF. We log the user out of the module
				RequestContextHolder.currentRequestAttributes().session.user = null

				throw new NotAuthenticatedException("User is not authenticated with GSCF. Requested URL: $addr")
				break
			case 404:    // Resource not found
				throw new ResourceNotFoundException("Specified resource could not be found: $addr")
				break
			case 500:    // Internal server error
				throw new Exception("An unknown error occured when calling service: $addr response: $connection.responseMessage")
				break
			default:
				try {

					String responseText = connection.content.text

					def jsonResponse = JSON.parse(responseText)

					if (responseText.size() > 2000)
						log.info("GSCF REST-RESP: ${responseText[0..2000]}...")
					else
						log.info("GSCF REST-RESP: $responseText")

					if (jsonResponse instanceof JSONArray)
						jsonResponse.collect { jsonElement ->
							def m = [:]
							jsonElement.each { m << it }
							m
						}
					else {
						jsonResponse.each { gscfResponse << it }

						[gscfResponse]
					}

				} catch (Exception e) {
					log.error("Parsing GSCF JSON response failed at $addr. Reponse was $connection.content.text", e)
					throw new Exception("Parsing GSCF JSON response failed at $addr.  Please check log for more information.", e)
				}
				break
		}
	}

	/**************************************************************************
	 * Shorthand methods for getting URLs
	 **************************************************************************/

	/**
	 * Returns the url to show details of a study in GSCF
	 *
	 * @param study Study object to view in GSCF
	 * @return URL to redirect the user to
	 */
	String urlViewStudy(String studyToken) {
		ConfigurationHolder.config.gscf.baseURL + '/study/showByToken/' + studyToken
	}

	/**
	 * Returns the url to add a new study in GSCF
	 *
	 * @return URL to redirect the user to
	 */
	String urlAddStudy(String studyToken) {
		ConfigurationHolder.config.gscf.baseURL + ConfigurationHolder.config.gscf.addStudyPath
	}

	/**
	 * Returns the URL to register an external search with GSCF
	 *
	 * @return URL to redirect the user to
	 */
	String urlRegisterSearch() {
		ConfigurationHolder.config.gscf.baseURL + ConfigurationHolder.config.gscf.registerSearchPath
	}

	/**
	 * Base URL of GSCF Rest Controller/API
	 *
	 * @return url String
	 */
	String restURL() {
		ConfigurationHolder.config.gscf.baseURL + '/rest'
	}

	/**
	 * Consumer ID for connection to of GSCF Rest Controller/API
	 *
	 * @return consumerId    String
	 */
	private String consumerId() {
		ConfigurationHolder.config.module.consumerId
	}

	/**
	 * Module URL for connection to of GSCF Rest Controller/API
	 *
	 * @return moduleURL    String
	 */
	private String moduleURL() {
		ConfigurationHolder.config.grails.serverURL
	}

}
