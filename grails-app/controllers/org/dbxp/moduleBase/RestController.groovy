package org.dbxp.moduleBase

import grails.converters.*

import org.codehaus.groovy.grails.commons.ConfigurationHolder

/** 
 *
 * @author Robert Horlings (robert@isdat.nl)
 * @since 20101229
 * @see   SAM.RestController
 *
 * $Rev$
 *
 * This class provides a REST-full service for getting and setting the data
 * in the Metagenomics Module. The service consists of several
 * resources listed below. So far, all resources are GET resoruces, i.e. we
 * do not use PUT, POST or DELETE. Because we are using Grails' web libaries,
 * each resource corresponds to exactly one action of this controller.
 *
 *
 * The REST resources implemented in this controller are:
 *
 * rest/notifyStudyChange(studyToken)
 * 
 * 
 *
 */
class RestController {
	def synchronizationService
	
	/****************************************************************/
	/* REST resource for handling study change in GSCF              */
	/****************************************************************/
	
	/**
	 * Is called by GSCF when a study is added, changed or deleted.
	 * Sets the 'dirty' flag of a study to true, so that it will be updated
	 * next time the study is asked for.
	 * 
	 * @param	studyToken
	 */
	def notifyStudyChange = {
		def studyToken = params.studyToken

		if( !studyToken ) {
			response.sendError(400, "No studyToken given" )
			return
		}

		// Search for the changed study
		def study = Study.findByStudyToken( studyToken );

		// If authentication is given, we can synchronize immediately
		def synchronizeNow = session.user ? true : false;
		
		// If the study is not found, it is added in GSCF. Add a dummy (dirty) study, in order to
		// update it immediately when asked for
		if( !study ) {
			log.info( "GSCF notification for new study " + studyToken );

			def studyClass = synchronizationService.determineClassFor( "Study" );
			study = studyClass.newInstance(
				name: "",
				studyToken: studyToken,
				gscfVersion: 0,
				isDirty: true
			);
			
		} else {
			log.info( "GSCF notification for existing study " + studyToken );
			study.isDirty = true;
		}
		
		study.save(flush:true);
		
		// Synchronize study if needed
		if( synchronizeNow ) {
			synchronizationService.synchronizeStudy( study );
		}

		def jsonData = [ 'studyToken': studyToken, message: "Notify succesful" ];
		render jsonData as JSON
	}

	/**
	 * Return URL to view an assay.
	 *
	 * @param  assayToken
	 * @return URL to view an assay as hash map with key 'url'.
	 *
	 */
	def getAssayURL = {
		throw new org.apache.commons.lang.NotImplementedException();
	}

	/***************************************************/
	/* REST resources related to the querying in GSCF  */
	/***************************************************/

	/**
	 * Retrieves a list of fields that could be queried when searching for a specific entity.
	 * 
	 * The module is allowed to return different fields when the user searches for different entities
	 * 
	 * Example call: 		[moduleurl]/rest/getQueryableFields?entity=Study&entity=Sample
	 * Example response:	{ "Study": [ "# sequences" ], "Sample": [ "# sequences", "# bacteria" ] }
	 * 
	 * @param	params.entity	Entity that is searched for. Might be more than one. If no entity is given, 
	 * 							a list of searchable fields for all entities is given
	 * @return	JSON			List with the names of the fields 
	 */
	def getQueryableFields = {
		throw new org.apache.commons.lang.NotImplementedException();
	}
	
	/**
	 * Returns data for the given field and entities.
	 * 
	 * Example call: 		[moduleurl]/rest/getQueryableFieldData?entity=Study&tokens=abc1&tokens=abc2&fields=# sequences&fields=# bacteria
	 * Example response:	{ "abc1": { "# sequences": 141, "# bacteria": 0 }, "abc2": { "#sequences": 412 } }
	 * 
	 * @param	params.entity	Entity that is searched for
	 * @param	params.tokens	One or more tokens of the entities that the data should be returned for
	 * @param	params.fields	One or more field names of the data to be returned. If no fields are given, all fields are returned
	 * @return	JSON			Map with keys being the entity tokens and the values being maps with entries [field] = [value]. Not all
	 * 							fields and tokens that are asked for have to be returned by the module (e.g. when a specific entity can 
	 * 							not be found, or a value is not present for an entity)
	 */
	def getQueryableFieldData = {
		throw new org.apache.commons.lang.NotImplementedException();
	}

	/****************************************************************/
	/* REST resources for exporting data from GSCF (after searching	*/
	/****************************************************************/

	/**
	 * Retrieves a list of actions that can be performed on data with a specific entity. This includes actions that
	 * refine the search result. 
	 *
	 * The module is allowed to return different fields when the user searches for different entities
	 *
	 * Example call: 		[moduleurl]/rest/getPossibleActions?entity=Assay&entity=Sample
	 * Example response:	{ "Assay": [ { name: "excel", description: "Export as excel" } ], 
	 * 						  "Sample": [ { name: "excel", description: "Export as excel" }, { name: "fasta", description: : "Export as fasta" } ] }
	 *
	 * @param	params.entity	Entity that is searched for. Might be more than one. If no entity is given,
	 * 							a list of searchable fields for all entities is given
	 * @return	JSON			Hashmap with keys being the entities and the values are lists with the action this module can 
	 * 							perform on this entity. The actions as hashmaps themselves, with keys 
	 * 							'name'			Unique name of the action, as used for distinguishing actions
	 * 							'description'	Human readable description
	 * 							'url'			URL to send the user to when performing this action. The user is sent there using POST with
	 * 											the following parameters:
	 * 												actionName:		Name of the action to perform
	 * 												name:			Name of the search that the action resulted from
	 * 												url:			Url of the search that the action resulted from
	 * 												entity:			Type of entity being returned
	 * 												tokens:			List of entity tokens 
	 * 							'type'			(optional) Determines what type of action it is. Possible values: 'default', 'refine', 'export', ''
	 */
	def getPossibleActions = {
		throw new org.apache.commons.lang.NotImplementedException();
	}
	
	/****************************************************************/
	/* REST resources for providing basic data to the GSCF          */
	/****************************************************************/

	/**
	 * Return a list of simple assay measurements matching the querying text.
	 *
	 * @param assayToken
	 * @return list of measurements for token. Each member of the list is a hash.
	 *			the hash contains the three keys values pairs: value, sampleToken, and
	 *			measurementMetadata.
	 *
	 * Example REST call:
	 * http://localhost:8184/metagenomics/rest/getMeasurements/query?assayToken=16S-5162
	 *
	 * Resulting JSON object:
	 *
	 * [ "# sequences", "average quality" ]
	 *
	 */
	def getMeasurements = {
		throw new org.apache.commons.lang.NotImplementedException();
	}

	/**
	 * Return measurement metadata for measurement
	 *
	 * @param assayToken
	 * @param measurementTokens. List of measurements for which the metadata is returned.
	 *                           If this is not given, then return metadata for all
	 *                           measurements belonging to the specified assay.
	 * @return list of measurements
	 *
	 * Example REST call:
	 * http://localhost:8184/metagenomics/rest/getMeasurementMetadata/query?assayToken=16S-5162
	 *      &measurementToken=# sequences
	 *		&measurementToken=average quality
	 *
	 * Example resulting JSON object:
	 *
	 * [ {"name":"# sequences","type":"raw"},
	 *   {"name":"average quality", "unit":"Phred"} ]
	 */
	def getMeasurementMetaData = {
		throw new org.apache.commons.lang.NotImplementedException();
	}

	/**
	 * Return list of measurement data.
	 *
	 * @param assayTokes
	 * @param measurementToken. Restrict the returned data to the measurementTokens specified here.
	 * 						If this argument is not given, all samples for the measurementTokens are returned.
	 * 						Multiple occurences of this argument are possible.
	 * @param sampleToken. Restrict the returned data to the samples specified here.
	 * 						If this argument is not given, all samples for the measurementTokens are returned.
	 * 						Multiple occurences of this argument are possible.
	 * @param boolean verbose. If this argument is not present or it's value is true, then return
	 *                      the date in a redundant format that is easier to process.
	 *						By default, return a more compact JSON object as follows.
	 *
	 * 						The list contains three elements:
	 *
	 *						(1) a list of sampleTokens,
	 *						(2) a list of measurementTokens,
	 * 						(3) a list of values.
	 *
	 * 						The list of values is a matrix represented as a list. Each row of the matrix
	 * 						contains the values of a measurementToken (in the order given in the measurement
	 * 						token list, (2)). Each column of the matrix contains the values for the sampleTokens
	 * 						(in the order given in the list of sampleTokens, (1)).
	 * 						(cf. example below.)
	 *
	 *
	 * @return  table (as hash) with values for given samples and measurements
	 *
	 *
	 * List of examples.
	 *
	 *
	 * Example REST call:
	 * http://localhost:8184/metagenomics/rest/getMeasurementData/doit?assayToken=PPSH-Glu-A
	 *    &measurementToken=total carbon dioxide (tCO)
	 *    &sampleToken=5_A
	 *    &sampleToken=1_A
	 *    &verbose=true
	 *
	 * Resulting JSON object:
	 * [ {"sampleToken":"1_A","measurementToken":"total carbon dioxide (tCO)","value":28},
	 *   {"sampleToken":"5_A","measurementToken":"total carbon dioxide (tCO)","value":29} ]
	 *
	 *
	 *
	 * Example REST call without sampleToken, without measurementToken,
	 *    and with verbose representation:
	 * http://localhost:8184/metagenomics/rest/getMeasurementData/dossit?assayToken=PPSH-Glu-A
	 *    &verbose=true
	 *
	 * Resulting JSON object:
	 * [ {"sampleToken":"1_A","measurementToken":"sodium (Na+)","value":139},
	 *	 {"sampleToken":"1_A","measurementToken":"potassium (K+)","value":4.5},
	 *	 {"sampleToken":"1_A","measurementToken":"total carbon dioxide (tCO)","value":26},
	 *	 {"sampleToken":"2_A","measurementToken":"sodium (Na+)","value":136},
	 *	 {"sampleToken":"2_A","measurementToken":"potassium (K+)","value":4.3},
	 *	 {"sampleToken":"2_A","measurementToken":"total carbon dioxide (tCO)","value":28},
	 *	 {"sampleToken":"3_A","measurementToken":"sodium (Na+)","value":139},
	 *	 {"sampleToken":"3_A","measurementToken":"potassium (K+)","value":4.6},
	 *	 {"sampleToken":"3_A","measurementToken":"total carbon dioxide (tCO)","value":27},
	 *	 {"sampleToken":"4_A","measurementToken":"sodium (Na+)","value":137},
	 *	 {"sampleToken":"4_A","measurementToken":"potassium (K+)","value":4.6},
	 *	 {"sampleToken":"4_A","measurementToken":"total carbon dioxide (tCO)","value":26},
	 *	 {"sampleToken":"5_A","measurementToken":"sodium (Na+)","value":133},
	 *	 {"sampleToken":"5_A","measurementToken":"potassium (K+)","value":4.5},
	 *	 {"sampleToken":"5_A","measurementToken":"total carbon dioxide (tCO)","value":29} ]
	 *
	 *
	 *
	 * Example REST call with default (non-verbose) view and without sampleToken:
	 *
	 * Resulting JSON object:
	 * http://localhost:8184/metagenomics/rest/getMeasurementData/query?
	 * 	assayToken=PPSH-Glu-A&
	 *	measurementToken=total carbon dioxide (tCO)
	 *
	 * Resulting JSON object:
	 * [ ["1_A","2_A","3_A","4_A","5_A"],
	 *   ["sodium (Na+)","potassium (K+)","total carbon dioxide (tCO)"],
	 *   [139,136,139,137,133,4.5,4.3,4.6,4.6,4.5,26,28,27,26,29] ]
	 *
	 * Explanation:
	 * The JSON object returned by default (i.e., unless verbose is set) is an array of three arrays.
	 * The first nested array gives the sampleTokens for which data was retrieved.
	 * The second nested array gives the measurementToken for which data was retrieved.
	 * The thrid nested array gives the data for sampleTokens and measurementTokens.
	 *
	 *
	 * In the example, the matrix represents the values of the above Example and
	 * looks like this:
	 *
	 * 			1_A		2_A		3_A		4_A		5_A
	 *
	 * Na+		139		136		139		137		133
	 *
	 * K+ 		4.5		4.3		4.6		4.6		4.5
	 *
	 * tCO		26		28		27		26		29
	 *
	 */
	def getMeasurementData = {
		throw new org.apache.commons.lang.NotImplementedException();
	}
	
	/* helper function for getMeasurementData
	 *
	 * Return compact JSON object for data. The format of the returned array is as follows.
	 *
	 * The list contains three elements:
	 *
	 * (1) a list of sampleTokens,
	 * (2) a list of measurementTokens,
	 * (3) a list of values.
	 *
	 * The list of values is a matrix represented as a list. Each row of the matrix
	 * contains the values of a measurementToken (in the order given in the measurement
	 * token list, (2)). Each column of the matrix contains the values for the sampleTokens
	 * (in the order given in the list of sampleTokens, (1)).
	 */
	def compactTable( results ) {
		def sampleTokens = results.collect( { it['sampleToken'] } ).unique()
		def measurementTokens = results.collect( { it['measurementToken'] } ).unique()

		def data = []
		measurementTokens.each{ m ->
			sampleTokens.each{ s ->
				def item = results.find{ it['sampleToken']==s && it['measurementToken']==m }
				data.push item ? item['value'] : null
			}
		}

		return [ sampleTokens, measurementTokens, data ]
	}

}
