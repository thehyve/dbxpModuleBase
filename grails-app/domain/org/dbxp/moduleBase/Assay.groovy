package org.dbxp.moduleBase

/**
 * Minimal representation of an assay. The studyToken is the link with an assay object in GSCF.
 * 
 * @see GscfService.getAssay
 */
class Assay {
	String assayToken
	String name
	
	static belongsTo = [ study: Study ]
	static hasMany = [ samples: Sample ]
	
	static mapping = {
		columns {
			assayToken index:'assaytoken_idx'
		}
	}
	
	static constraints = {
		assayToken(unique:true)
	}
	
	public String token() { return assayToken; }
	
	public String toString() {
		return "Assay " + id + ": " + ( name ?: "" )
	}
	
	/**
	* Removes all assay samples from this assay
	*/
   public void removeSamples() {
	   if( samples == null )
		   return
		   
	   def samples = samples.toArray();
	   def numSamples = samples.size();
	   for( int i = numSamples - 1; i >= 0; i-- ) {
		   def existingSample = samples[i];
		   
		   // Delete the sample and also the association
		   existingSample.delete()
		   removeFromSamples( existingSample );
	   }
   }
}
