package org.dbxp.moduleBase

import org.codehaus.groovy.grails.commons.ConfigurationHolder

class SynchronizationService {
	// The number of times the system tries to save a record, if it fails due to concurrency
	// If concurrency issues occur, the system will start the specific step again. It loads the object
	// from the database again, and repeats the process. After this number of tries, if it still fails,
	// the process will throw the exception.
	protected static final int NUM_TRIES_FOR_CONCURRENCY = 5;

	def gscfService
	def grailsApplication

	String sessionToken = null  // Session token to use for communication
	User user = null            // Currently logged in user. Must be set when synchronizing authorization
	boolean eager = false       // When set to true, this method fetches data about all studies from GSCF. Otherwise, it will only look at the
								// studies marked as dirty in the database. Defaults to false.

	static transactional = 'mongo'//true

	/**
	 * Initialize this synchronizationService
	 * @param sessionToken	Sessiontoken to be used for authentication
	 * @param user			User that has been logged in. Has to be set when synchronizing authorization
	 * @return
	 */
	public void initSynchronization( sessionToken, user ) {
		this.sessionToken = sessionToken;
		this.user = user;
	}

	/**
	 * Performs a full synchronization in order to retrieve all studies
	 * @return
	 */
	public void fullSynchronization() throws Exception {
		asSynchronization {
			def previousEager = this.eager
			this.eager = true
			this._synchronizeStudies()
			this.eager = previousEager
		}
	}

	/**
	 * Perfoms a synchronization for all studies that have been changed
	 * @return
	 */
	public ArrayList<Study> synchronizeChangedStudies() throws Exception {
		asSynchronization {
			return _synchronizeChangedStudies();
		}
	}

	public ArrayList<Study> synchronizeStudies() throws BadRequestException, NotAuthenticatedException, NotAuthorizedException, ResourceNotFoundException, Exception {
		asSynchronization {
			return _synchronizeStudies()
		}
	}

	/**
	 * Synchronizes the given study with the data from GSCF
	 * @param study Study to synchronize
	 * @return Study    Synchronized study or null if the synchronization has failed
	 */
	public Study synchronizeStudy(Study study) throws Exception {
		asSynchronization {
			return _synchronizeStudy( study )
		}
	}


	/**
	 * Retrieves the authorization for the currently logged in user
	 * Since GSCF only provides authorization information about the currently
	 * logged in user, we can not guarantee that the authorization information
	 * is synchronized for all users.
	 *
	 * Make sure synchronizationService.user is set beforehand
	 *
	 * @param study Study to synchronize authorization for
	 * @return Auth object for the given study and user or null is the study has been deleted
	 */
	public Auth synchronizeAuthorization(Study study) throws Exception {
		asSynchronization {
			return _synchronizeAuthorization( study )
		}
	}

	/**
	 * Synchronizes the given assay with the data from GSCF
	 * @param assay Assay to synchronize
	 * @return Assay    Synchronized assay or null if the synchronization has failed
	 */
	public Assay synchronizeAssay(Assay assay) throws Exception {
		asSynchronization {
			return _synchronizeAssay( assay )
		}
	}

	/**
	 * Determines whether the synchronization should be performed or not. This can be entered
	 * in configuration, to avoid synchronization when developing.
	 * @return
	 */
	protected performSynchronization() {
		def conf = ConfigurationHolder.config.module.synchronization.perform

		// If nothing is entered in configuration, return true (default value)
		if (conf == null)
			return true

		// See http://jira.codehaus.org/browse/GRAILS-6515
		if (conf.class == java.lang.Boolean) {
			// because 'true.toBoolean() == false' !!!
			return conf
		} else {
			return conf.asBoolean()
		}
	}


	/**
	 * Perfoms a synchronization for all studies that have been changed
	 * @return
	 */
	protected ArrayList<Study> _synchronizeChangedStudies() {
		if (!performSynchronization())
			return

		// Determine what studies have been changed
		def studyVersions

		try {
			studyVersions = gscfService.getStudyVersions( sessionToken );
			log.trace "Retrieved study versions: " + studyVersions
		} catch( NotAuthenticatedException e ) {
			// If a not authenticated exception occurs, GSCF is probably not able to handle anonymous synchronization. This has
			// been implemented only since july 2011 (r1968)
			throw e
		} catch( Exception e ) {
			// If another exception occurs, most probably GSCF doesn't know the getStudyVersions call. This has only been implemented
			// since june 2011 (r1941)
			throw new Exception( "In order to use synchronization, you need at least GSCF version 0.8.5 (r1941). Either update your GSCF instance or disable synchronization in the configuration (module.synchronization.perform = false)", e)
		}

		// Mark studies dirty that have a different version number in our database
		def studyTokens = studyVersions.collect { it.studyToken }
		def studies = []

		if( studyTokens )
			studies = Study.findAll( "FROM Study s WHERE s.studyToken IN (:tokens)", [ "tokens": studyTokens ] )

		studyVersions.each { gscfStudy ->
			tryWithConcurrencyCheck {
				log.trace "Synchronizing study " + gscfStudy

				def gscfVersion = gscfStudy.version ?: -1;
				def localStudy = studies.find { it.studyToken == gscfStudy.studyToken }

				log.trace "  Local study found: " + localStudy

				// If the study from GSCF is not found in this database, it has been added
				// in GSCF. Create a new object in order to be synchronized
				if( !localStudy ) {
					def domainClass = determineClassFor( "Study" );

					log.trace "  Not found locally. Creating an object: " + domainClass;

					localStudy = domainClass.newInstance();
					localStudy.setPropertiesFromGscfJson( gscfStudy );
					localStudy.isDirty = true;
				} else if( gscfVersion != localStudy.gscfVersion ){
					log.trace "  Mark existing study dirty"
					// Mark existing study dirty if the versions don't match
					localStudy.isDirty = true
				}

				localStudy.save( flush: true );
			}
		}

		// Mark all studies that this user can read, but were not returned by GSCF as dirty;
		// those studies are either deleted or authorization has changed
		def unknownStudies

		tryWithConcurrencyCheck {
			if( studyTokens ) {
				if( user ) {
					unknownStudies = Study.findAll( "FROM Study s WHERE s.studyToken NOT IN (:tokens) AND exists( FROM Auth a WHERE a.study = s AND a.user = :user AND a.canRead = true )", [ "tokens": studyTokens, "user": user ] );
				} else {
					unknownStudies = Study.findAll( "FROM Study s WHERE s.studyToken NOT IN (:tokens) AND s.isPublic = true", [ "tokens": studyTokens ] );
				}
			} else {
				if( user ) {
					unknownStudies = Study.findAll( "FROM Study s WHERE exists( FROM Auth a WHERE a.study = s AND a.user = :user AND a.canRead = true )", [ "user": user ] );
				} else {
					unknownStudies = Study.findAll( "FROM Study s WHERE s.isPublic = true" );
				}
			}

			log.trace "Marking " + unknownStudies?.size() + " studies as dirty since the authorization has changed";

			unknownStudies?.each {
				it.isDirty = true;
				it.save();
			}

		}

		// Synchronize dirty studies
		return _synchronizeStudies()
	}

	/**
	 * Synchronizes all studies with the data from GSCF.
	 * @return ArrayList    List of studies or null if the synchronization has failed
	 */
	protected ArrayList<Study> _synchronizeStudies() throws BadRequestException, NotAuthenticatedException, NotAuthorizedException, ResourceNotFoundException, Exception {
		if (!performSynchronization())
			return Study.list()

		// When eager fetching is enabled, ask for all studies, otherwise only ask for studies marked dirty
		// Synchronization is performed on all studies, not only the studies the user has access to. Otherwise
		// we would never notice that a user was given read-access to a study.
		def studies = []
		if (eager) {
			log.trace "Eager synchronization"
		} else {
			studies = Study.findAllWhere([isDirty: true])
			log.trace "Default synchronization: " + studies.size()

			// Perform no synchronization if no studies have to be synchronized
			if (studies.size() == 0)
				return []
		}

		// Perform synchronization on only one study directly, because otherwise
		// the getStudies method could throw a ResourceNotFoundException or NotAuthorizedException
		// that can better be handled by synchronizeStudy
		if (studies.size() == 1) {
			def newStudy = _synchronizeStudy((Study) studies[0])
			if (newStudy)
				return [newStudy]
			else
				return []
		}

		// Fetch all studies from GSCF
		def newStudies
		try {
			if (!eager) {
				def studyTokens = studies.studyToken

				if (studyTokens instanceof String) {
					studyTokens = [studyTokens]
				}

				newStudies = gscfService.getStudies(sessionToken, studyTokens)
			} else {
				newStudies = gscfService.getStudies(sessionToken)
			}
		} catch (Exception e) { // All exceptions are thrown.
			// Can't retrieve data. Maybe sessionToken has expired or invalid. Anyway, stop
			// synchronizing and return null
			log.error("Exception occurred when fetching studies: " + e.getMessage())
			throw e
		}

		synchronizeStudies(newStudies)
		studies = handleDeletedStudies(studies, newStudies)

		log.trace("Returning " + studies.size() + " studies after synchronization")

		return studies
	}

	/**
	 * Synchronizes all studies given by 'newStudies' with existing studies in the database, and adds them
	 * if they don't exist
	 *
	 * @param newStudies JSON object with studies as returned by GSCF
	 * @return
	 */
	protected synchronizeStudies(def newStudies) {
		// Synchronize all studies that are returned. Studies that are not returned by GSCF might be removed
		// but could also be invisible for the current user.
		newStudies.each { gscfStudy ->
			if (gscfStudy.studyToken) {
				log.trace("Processing GSCF study " + gscfStudy.studyToken + ": " + gscfStudy)

				Study studyFound = Study.findByStudyToken(gscfStudy.studyToken as String)

				if (studyFound) {
					log.trace("Study found with name " + studyFound.name)

					// Synchronize the study itself with the data retrieved
					_synchronizeStudy(studyFound, gscfStudy)
				} else {
					log.trace("Study not found. Creating a new one")

					def domainClass = determineClassFor( "Study" );

					// If it doesn't exist, create a new object
					tryWithConcurrencyCheck {
						studyFound = domainClass.newInstance();
						studyFound.setPropertiesFromGscfJson( gscfStudy );
						studyFound.isDirty = true
						studyFound.save(flush: true)
					}

					// Synchronize authorization and study assays (since the study itself is already synchronized)
					def auth = _synchronizeAuthorization(studyFound)
					if (auth && auth.canRead)
						synchronizeStudyAssays(studyFound)

					// Mark the study as clean (and first retrieve it from the database
					tryWithConcurrencyCheck {
						studyFound = studyFound.refresh();
						studyFound.isDirty = false
						studyFound.save()
					}
				}
			}
		}
	}

	/**
	 * Removes studies from the database that are expected but not found in the list from GSCF
	 * @param studies List with existing studies in the database that were expected in the output of GSCF
	 * @param newStudies JSON object with studies as returned by GSCF
	 * @return List of remaining studies
	 */
	protected ArrayList<Study> handleDeletedStudies(def studies, def newStudies) {
		// If might also be that studies have been removed from the system. In that case, the studies
		// should be deleted from this module as well. Looping backwards in order to avoid conflicts
		// when removing elements from the list

		def numStudies = studies.size()
		for (int i = numStudies - 1; i >= 0; i--) {
			def existingStudy = studies[i]

			def studyFound = newStudies.find { it.studyToken == existingStudy.studyToken }

			if (!studyFound) {
				log.trace("Study " + existingStudy.studyToken + " not found. Check whether it is removed or the user just can't see it.")

				// Study was not given to us by GSCF. This might be because the study is removed, or because the study is not visible (anymore)
				// to the current user.
				// Synchronize authorization and see what is the case (it returns null if the study has been deleted)
				if (_synchronizeAuthorization(existingStudy) == null) {
					// Update studies variable to keep track of all existing studies
					studies.remove(existingStudy)
				}
			}
		}

		return studies
	}

	/**
	 * Synchronizes the given study with the data from GSCF
	 * @param study Study to synchronize
	 * @return Study    Synchronized study or null if the synchronization has failed
	 */
	protected Study _synchronizeStudy(Study study) {
		if (!performSynchronization())
			return study

		if (study == null)
			return null

		// If the study hasn't changed, don't update anything
		if (!eager && !study.isDirty)
			return study

		// Retrieve the study from GSCF
		def newStudy
		try {
			newStudy = gscfService.getStudy(sessionToken, study.studyToken)
		} catch (NotAuthorizedException e) {
			// User is not authorized to access this study. Update the authorization within the module and return
			_synchronizeAuthorization(study)
			return null
		} catch (ResourceNotFoundException e) {
			// Study can't be found within GSCF.
			deleteStudy( study );
			return null
		} catch (Exception e) { // All other exceptions
			// Can't retrieve data. Maybe sessionToken has expired or invalid. Anyway, stop
			// synchronizing and return null
			e.printStackTrace()
			log.error("Exception occurred when fetching study " + study.studyToken + ": " + e.getMessage())
			throw new Exception("Error while fetching study " + study.studyToken, e)
		}

		// If no study is returned, something went wrong.
		if (newStudy.size() == 0) {
			throw new Exception("No data returned for study " + study.studyToken + " but no error has occurred either. Please contact your system administrator")
		}

		return _synchronizeStudy(study, newStudy)
	}

	/**
	 * Synchronizes the given study with the data from GSCF
	 * @param study Study to synchronize
	 * @param newStudy Data to synchronize the study with
	 * @return Study        Synchronized study or null if the synchronization has failed
	 */
	protected Study _synchronizeStudy(Study study, def newStudy) {
		if (!performSynchronization())
			return study

		if (study == null || newStudy == null)
			return null

		// If the study hasn't changed, don't update anything
		if (!eager && !study.isDirty) {
			return study
		}

		// If no study is returned, something went wrong.
		if (newStudy.size() == 0) {
			return null
		}

		def publicStudy = newStudy.published && newStudy[ 'public' ];

		// Mark study dirty to enable synchronization
		def auth = _synchronizeAuthorization(study)

		if (auth?.canRead || publicStudy)
			synchronizeStudyAssays(study)

		// Update properties and mark as clean. First retrieve a new update of this study
		tryWithConcurrencyCheck {
			study.refresh();
			study.setPropertiesFromGscfJson( newStudy );
			study.isDirty = false
			study.save(flush: true)
		}

		return study
	}

	/**
	 * Synchronizes the assays of the given study with the data from GSCF
	 * @param study Study of which the assays should be synchronized
	 * @return ArrayList    List of assays or null if the synchronization has failed
	 */
	protected ArrayList<Assay> synchronizeStudyAssays(Study study) {
		if (!performSynchronization())
			return study.assays.toList()

		if (!eager && !study.isDirty)
			return study.assays as List

		// Also update all assays, belonging to this study
		// Retrieve the assays from GSCF
		def newAssays
		try {
			newAssays = gscfService.getAssays(sessionToken, study.studyToken)
		} catch (Exception e) { // All exceptions are thrown. If we get a NotAuthorized or NotFound Exception, something has changed in between the two requests. This will result in an error
			// Can't retrieve data. Maybe sessionToken has expired or invalid. Anyway, stop
			// synchronizing and return null
			log.error("Exception occurred when fetching assays for study " + study.studyToken + ": " + e.getMessage())
			throw new Exception("Error while fetching samples for assay " + study.studyToken, e)
		}

		// If no assay is returned, we remove all assays
		// from this study and return an empty list
		if (newAssays.size() == 0 && study.assays != null) {

			def studyAssays = study.assays.toArray()
			def numStudyAssays = study.assays.size()
			for (int i = numStudyAssays - 1; i >= 0; i--) {
				def existingAssay = studyAssays[i]

				// Move data to trash
				deleteAssay( existingAssay );
			}

			return []
		}

		synchronizeStudyAssays(study, newAssays)
		return handleDeletedAssays(study, newAssays)
	}

	/**
	 * Synchronizes the assays of a study with the given data from GSCF
	 * @param study Study to synchronize
	 * @param newAssays JSON object given by GSCF to synchronize the assays with
	 */
	protected void synchronizeStudyAssays(Study study, def newAssays) {
		// Otherwise, we search for all assays in the new list, if they
		// already exist in the list of assays
		newAssays.each { gscfAssay ->
			if (gscfAssay.assayToken) {
				log.trace("Processing GSCF assay " + gscfAssay.assayToken + ": " + gscfAssay)

				Assay assayFound = study.assays.find { it.assayToken == gscfAssay.assayToken }

				if (assayFound) {
					log.trace("Assay found with name " + assayFound.name)

					// Synchronize the assay itself with the data retrieved
					_synchronizeAssay(assayFound, gscfAssay)
				} else {
					log.trace("Assay not found in study. Creating a new one")

					// If it doesn't exist, create a new object
					tryWithConcurrencyCheck {
						study.refresh();

						def domainClass = determineClassFor( "Assay" );
						assayFound = domainClass.newInstance();
						assayFound.study = study;
						assayFound.setPropertiesFromGscfJson( gscfAssay );

						log.trace("Connecting assay to study")
						study.addToAssays(assayFound)
						assayFound.save(flush: true)
					}

					// Synchronize assay samples (since the assay itself is already synchronized)
					synchronizeAssaySamples(assayFound)
				}
			}
		}
	}

	/**
	 * Removes assays from the system that have been deleted from GSCF
	 * @param study Study to synchronize
	 * @param newAssays JSON object given by GSCF to synchronize the assays with
	 * @return List with all assays from the study
	 */
	protected ArrayList<Assay> handleDeletedAssays(Study study, def newAssays) {
		if (study.assays == null) {
			return []
		}

		// If might also be that assays have been removed from this study. In that case, the removed assays
		// should be deleted from this study in the module as well. Looping backwards in order to avoid conflicts
		// when removing elements from the list
		def assays = study.assays.toArray()
		def numAssays = assays.size()
		for (int i = numAssays - 1; i >= 0; i--) {
			def existingAssay = assays[i]

			Assay assayFound = (Assay) newAssays.find { it.assayToken == existingAssay.assayToken }

			if (!assayFound) {
				log.trace("Assay " + existingAssay.assayToken + " not found. Removing it.")

				// The assay has been removed
				deleteAssay( existingAssay );
			}
		}

		return study.assays.toList()
	}

	/**
	 * Retrieves the authorization for the currently logged in user
	 * Since GSCF only provides authorization information about the currently
	 * logged in user, we can not guarantee that the authorization information
	 * is synchronized for all users.
	 *
	 * Make sure synchronizationService.user is set beforehand
	 *
	 * @param study Study to synchronize authorization for
	 * @return Auth object for the given study and user or null is the study has been deleted
	 */
	protected Auth _synchronizeAuthorization(Study study) {
		if (!performSynchronization())
			return Auth.findByUserAndStudy(user, study)

		// If the user is not set, we can't save anything to the database.
		if (user == null) {
			log.warn "Property user of SynchronizationService must be set to the currently logged in user in order for the authorization to be synchronized: " + study
			return;
		}

		// Only perform synchronization if needed. It is needed if:
		//    synchronization is eager OR
		//	  study is dirty OR
		//    we don't have authorization for this study and user
		if (!eager && !study.isDirty) {
			def existingAuth = Auth.findByUserAndStudy(user, study)
			if( existingAuth ) {
				return existingAuth;
			}
		}

		def gscfAuthorization
		try {
			gscfAuthorization = gscfService.getAuthorizationLevel(sessionToken, study.studyToken)
		} catch (ResourceNotFoundException e) {
			// Study has been deleted, remove all authorization on that study
			log.trace("Study " + study.studyToken + " has been deleted. Remove all authorization on that study")
			deleteStudy( study );

			return null
		}

		Auth a
		
		tryWithConcurrencyCheck {
			study.refresh();
			user.refresh();

			// Update the authorization object, or create a new one
			a = Auth.authorization(study, user)

			if (!a) {
				log.trace("Authorization not found for " + study.studyToken + " and " + user.username + ". Creating a new object")

				a = Auth.createAuth(study, user)
			}

			// Copy properties from gscf object
			if (gscfAuthorization.canRead instanceof Boolean)
				a.canRead = gscfAuthorization.canRead.booleanValue()

			if (gscfAuthorization.canWrite instanceof Boolean)
				a.canWrite = gscfAuthorization.canWrite.booleanValue()

			if (gscfAuthorization.isOwner instanceof Boolean)
				a.isOwner = gscfAuthorization.isOwner.booleanValue()

			a.save()
		}

		// Remove all authorization for other users, because otherwise the authorization might be out of sync
		// and we can not check the authorization for other users than the user currently logged in.
		// This is only needed if we have synchronized authorization for a dirty study.
		if( study.isDirty ) {
			Auth.executeUpdate("DELETE FROM Auth WHERE study = :study AND user <> :user", ["study": study, "user": user])
		}

		return a
	}

	/**
	 * Synchronizes the given assay with the data from GSCF
	 * @param assay Assay to synchronize
	 * @return Assay    Synchronized assay or null if the synchronization has failed
	 */
	protected Assay _synchronizeAssay(Assay assay) {
		if (!performSynchronization())
			return assay

		if (assay == null)
			return null

		// Only perform synchronization if needed
		if (!eager && !assay.study.isDirty)
			return assay

		// Retrieve the assay from GSCF
		def newAssay
		try {
			newAssay = gscfService.getAssay(sessionToken, assay.study.studyToken, assay.assayToken)
		} catch (NotAuthorizedException e) {
			// User is not authorized to access this study. Update the authorization within the module and return
			_synchronizeAuthorization(assay.study)
			return null
		} catch (ResourceNotFoundException e) {
			// Assay can't be found within GSCF.
			deleteAssay( assay );
			return null
		} catch (Exception e) { // All other exceptions are thrown
			// Can't retrieve data. Maybe sessionToken has expired or invalid. Anyway, stop
			// synchronizing and return null
			log.error("Exception occurred when fetching assay " + assay.assayToken + ": " + e.getMessage())
			throw new Exception("Error while fetching assay " + assay.assayToken, e)
		}

		// If new assay is empty, this means that the assay does exist, but now belongs to another module. Remove it from our system
		if (newAssay.size() == 0) {
			log.info("No data is returned by GSCF for assay  " + assay.assayToken + "; probably the assay is connected to another module.")
			deleteAssay( assay );
			return null
		}

		return synchronizeAssay(assay, newAssay)
	}

	/**
	 * Synchronizes the given assay with the data given
	 * @param assay Assay to synchronize
	 * @param newAssay New data for the assay, retrieved from GSCF
	 * @return Assay    Synchronized assay or null if the synchronization has failed
	 */
	protected Assay _synchronizeAssay(Assay assay, def newAssay) {
		if (!performSynchronization())
			return assay

		if (assay == null || newAssay == null)
			return null

		// Only perform synchronization if needed
		if (!eager && !assay.study.isDirty)
			return assay

		// If new assay is empty, something went wrong
		if (newAssay.size() == 0) {
			return null
		}

		log.trace("Assay is found in GSCF: " + assay.name + " / " + newAssay)
		tryWithConcurrencyCheck {
			assay.refresh();
			if( assay.isDifferentFromGscfJson( newAssay ) ) {
				assay.setPropertiesFromGscfJson( newAssay );
				assay.save()
			}
		}

		// Synchronize samples
		synchronizeAssaySamples(assay)

		return assay
	}

	/**
	 * Synchronizes the samples of a given assay with the data from GSCF
	 * @param assay Assay to synchronize
	 */
	protected ArrayList<Sample> synchronizeAssaySamples(Assay assay) {
		if (!performSynchronization())
			return []

		// If no assay is given, return null
		if (assay == null)
			return null

		// Retrieve the assay from GSCF
		def newSamples
		try {
			newSamples = gscfService.getSamples(sessionToken, assay.assayToken)
		} catch (NotAuthorizedException e) {
			// User is not authorized to access this study. Update the authorization within the module and return
			_synchronizeAuthorization(assay.study)
			return null
		} catch (ResourceNotFoundException e) {
			// Assay can't be found within GSCF. Samples will be removed
			deleteAssay( assay );

			return null
		} catch (Exception e) {
			// Can't retrieve data. Maybe sessionToken has expired or invalid. Anyway, stop
			// synchronizing and return null
			log.error("Exception occurred when fetching samples for assay " + assay.assayToken + ": " + e.getMessage())
			throw new Exception("Error while fetching samples for assay " + assay.assayToken, e)
		}

		// If no sample is returned, we remove all samples from the list
		if (newSamples.size() == 0) {
			assay.removeSamples()
			return []
		}

		synchronizeAssaySamples(assay, newSamples)
		return handleDeletedSamples(assay, newSamples)
	}

	/**
	 * Synchronize all samples for a given assay with the data from GSCF
	 * @param assay Assay to synchronize samples for
	 * @param newSamples New samples in JSON object, as given by GSCF
	 */
	protected void synchronizeAssaySamples(Assay assay, def newSamples) {
		// Otherwise, we search for all samples in the new list, if they
		// already exist in the list of samples
		newSamples.each { gscfSample ->
			log.trace("Processing GSCF sample " + gscfSample.sampleToken + ": " + gscfSample)
			if (gscfSample.name) {

				Sample sampleFound = assay.samples.find { it.sampleToken == gscfSample.sampleToken }

				if (sampleFound) {
					log.trace "Sample " + sampleFound.token() + " already found in database.";

					tryWithConcurrencyCheck {
						if( sampleFound.isDifferentFromGscfJson( gscfSample ) ) {
							sampleFound.setPropertiesFromGscfJson( gscfSample );
							sampleFound.save()
						}
					}
				} else {
					log.trace("Sample " + gscfSample.sampleToken + " not found in database. Creating a new object.")

					// If it doesn't exist, create a new object. First determine the class to use
					tryWithConcurrencyCheck {
						assay.refresh();

						def domainClass = determineClassFor( "Sample" );
						sampleFound = domainClass.newInstance()
						sampleFound.setPropertiesFromGscfJson( gscfSample );
						assay.addToSamples(sampleFound)

						if (!sampleFound.save( flush: true )) {
							log.error("Error while connecting sample to assay: " + sampleFound.errors)
						}
					}
				}
			}
		}
	}

	/**
	 * Removes samples from the system that have been removed from an assay in GSCF
	 * @param assay Assay to remove samples for
	 * @param newSamples JSON object with all samples for this assay as given by GSCF
	 */
	protected ArrayList<Sample> handleDeletedSamples(Assay assay, def newSamples) {
		// If might also be that samples have been removed from this assay. In that case, the removed samples
		// should be deleted from this assay. Looping backwards in order to avoid conflicts when removing elements
		// from the list
		if (assay.samples != null) {
			def assaySamples = assay.samples.toArray()
			def numSamples = assay.samples.size()
			for (int i = numSamples - 1; i >= 0; i--) {
				def existingSample = assaySamples[i]

				Sample sampleFound = (Sample) newSamples.find { it.sampleToken == existingSample.sampleToken }

				if (!sampleFound) {
					log.trace("Sample " + existingSample.sampleToken + " not found. Removing it.")

					// The sample has been removed
					deleteSample( existingSample );
				}
			}
		}

		// Create a list of samples to return
		if (assay.samples)
			return assay.samples.toList()
		else
			return []
	}

	/**
	 * Determines in what class to save objects from GSCF for a given entity
	 * 
	 * e.g.	determineClassFor( "Sample" ) returns org.dbxp.moduleBase.Sample by default,
	 * unless it is overridden by the configuration
	 * 
	 * @param entity	Sample, Assay or Study
	 * @return	Class
	 */
	protected Class determineClassFor( String entity ) {
		def domainClass
		def configurationClassName

		switch( entity ) {
			case "Study":
				domainClass = Study;
				configurationClassName = ConfigurationHolder.config.module.synchronization.classes.study
				break;
			case "Assay":
				domainClass = Assay;
				configurationClassName = ConfigurationHolder.config.module.synchronization.classes.assay
				break;
			case "Sample":
				domainClass = Sample;
				configurationClassName = ConfigurationHolder.config.module.synchronization.classes.sample
				break;
			default:
				return null;
		}

		if( configurationClassName ) {
			def configurationClass = grailsApplication.getDomainClass(configurationClassName).clazz

			// Make sure the given class is a subclass of our normal Sample object, so the
			// correct values can be set
			if( domainClass.isAssignableFrom( configurationClass ) )
				domainClass = configurationClass
			else
				log.warn "Synchronization domain class given for $entity (" + configurationClassName + ") is not a subclass of org.dbxp.moduleBase.$entity, while it should be.";
		}

		return domainClass
	}

	/**
	 * Deletes the given study from the database
	 * @param study
	 * @return
	 */
	protected boolean deleteStudy( study ) {
		log.debug "Deleting study " + study + " due to synchronization"
		if( study.assays ) {
			def l = [] + study.assays

			l.each { deleteAssay( it ) }
		}

		if( study.auth ) {
			def l = [] + study.auth

			l.each { it.delete() }
		}

		study.delete();

	}

	/**
	 * Deletes the given assay from the database
	 * @param assay
	 * @return
	 */
	protected boolean deleteAssay( assay ) {
		log.debug "Deleting assay " + assay + " due to synchronization"

		if( assay.samples ) {
			def l = [] + assay.samples

			l.each { deleteSample( it ) }
		}

		assay.study.removeFromAssays( assay );
		assay.delete();
	}

	/**
	 * Deletes the given sample from the database
	 * @param sample
	 * @return
	 */
	protected boolean deleteSample( sample ) {
		log.debug "Deleting sample " + sample + " due to synchronization"
		sample.assay.removeFromSamples( sample );
		sample.delete();
	}

	/**
	 * Execute an action while checking for concurrency issues. If those issues arise,
	 * the action will be repeated a number of times
	 * @see NUM_TRIES_FOR_CONCURRENCY
	 * @param action Action to execute
	 */
	protected void tryWithConcurrencyCheck( Closure action ) {
		def numTries = SynchronizationService.NUM_TRIES_FOR_CONCURRENCY;
		while( numTries > 0 ) {
			try {
				action();
				numTries = 0;	// Stop repeating this step
			} catch(org.springframework.dao.OptimisticLockingFailureException e) {
				// Synchronization has failed due to concurrency. Try again (if wanted)
				numTries -= 1;

				// If the user has tried too often, throw the exception
				if( numTries == 0 ) {
					throw e;
				}
			}
		}
	}
	
	/**
	 * Runs a piece of code while making sure the code (or another synchronization part)
	 * won't run simultaneously
	 * @param action	Code to execute
	 */
	protected def asSynchronization( Closure action ) {
		// Tell the system we are running a synchronization, and raise an 
		// error if it has already started
		SynchronizationService.startRunning();
		
		try {
			// Execute the action
			def returnData = action();
			
			// Make sure the system knows this synchronization has stopped,
			// and update the time of last synchronization
			SynchronizationService.stopRunning( true );
			
			// Send the data back to the user
			return returnData;	
		} catch( Exception e ) {
			// Make sure the system knows this synchronization has stopped,
			// but don't update the time of last synchronization
			SynchronizationService.stopRunning( false );
			throw e;
		}
	}

	/**
	 * Tells whether synchronization is currently running. Only one synchronization can run at the same moment
	 * so an exception will occur if starting synchronization again
	 */
	public static boolean isRunning = false;

	/**
	 * Last time the synchronization has run (has finished).
	 */
	public static def lastRun = null

	protected static void startRunning() {
		if( SynchronizationService.isRunning ) {
			throw new Exception( "Synchronization is already running." );
		}

		SynchronizationService.isRunning = true
	}

	protected static void stopRunning( success = true ) {
		SynchronizationService.isRunning = false;
		if( success ) {
			SynchronizationService.lastRun = System.currentTimeMillis();
		}
	}

}
