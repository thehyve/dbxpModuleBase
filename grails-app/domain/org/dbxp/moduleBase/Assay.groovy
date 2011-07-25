package org.dbxp.moduleBase

import java.io.Serializable;

/**
 * Minimal representation of an assay. The studyToken is the link with an assay object in GSCF.
 * 
 * @see GscfService.getAssay
 */
class Assay implements Serializable {
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
   
   /**
   * Sets the properties of this object, based on the JSON object given by GSCF
   * @param jsonObject	Object with assay data from GSCF
   */
  public void setPropertiesFromGscfJson( jsonObject ) {
	  this.assayToken = jsonObject.assayToken
	  this.name = jsonObject.name
  }

  /**
   * Determines whether the current object is different from the given JSON object
   * @param jsonObject	Object with assay data from GSCF
   * @return true if this object is different and needs updating
   */
  public boolean isDifferentFromGscfJson( jsonObject ) {
	  return this.name != jsonObject.name || this.assayToken != jsonObject.assayToken;
  }
   
   
   /**
	* Returns all assays this user can read
	* @param user	User for which the assays should be returned
	* @return		List of assay objects
	*/
   public static def giveReadableAssays( User user ) {
	   if( user )
		   return Assay.executeQuery( "SELECT DISTINCT a FROM Assay a, Auth auth WHERE ( auth.user = :user AND auth.study = a.study AND auth.canRead = true )", [ "user": user ] )
	   else
		   return Assay.executeQuery( "SELECT DISTINCT a FROM Assay a WHERE a.study.isPublic = true" )
   }
   
   /**
   * Returns all studies this user can write
	* @param user	User for which the assays should be returned
	* @return		List of assay objects
   */
  public static def giveWritableAssays( User user ) {
	  if( user )
		   return Assay.executeQuery( "SELECT DISTINCT a FROM Assay a, Auth auth WHERE ( auth.user = :user AND auth.study = a.study AND auth.canWrite = true )", [ "user": user ] )
	  else
		  return []
  }
  
  /**
  * Returns all assays this user owns
	* @param user	User for which the assays should be returned
	* @return		List of assay objects
  */
 public static def giveMyAssays( User user ) {
	 if( user )
		   return Assay.executeQuery( "SELECT DISTINCT a FROM Assay a, Auth auth WHERE ( auth.user = :user AND auth.study = a.study AND auth.isOwner = true )", [ "user": user ] )
	 else
		 return []
 }
}
