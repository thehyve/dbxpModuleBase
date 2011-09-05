package org.dbxp.moduleBase

/**
 * Minimal representation of a sample. The sampleToken is the link with a sample object in GSCF.
 *
 * @see GscfService.getSample
 */
class Sample implements Serializable {
	String  sampleToken		// Unique within an assay
	String  name

	static belongsTo    = [ assay: Assay ]

	static mapping = {
		columns {
			assay index:'assay_idx'
			sampleToken index:'sampletoken_idx'
		}
	}
	
	static constraints = {
	}

	public String token() { return sampleToken; }

	public String toString() {
		return "Sample " + id + ": " + ( name ?: "" )
	}

	/**
	 * Sets the properties of this object, based on the JSON object given by GSCF
	 * @param jsonObject	Object with sample data from GSCF
	 */
	public void setPropertiesFromGscfJson( jsonObject ) {
		this.sampleToken = jsonObject.sampleToken
		this.name = jsonObject.name
	}

	/**
	 * Determines whether the current object is different from the given JSON object
	 * @param jsonObject	Object with sample data from GSCF
	 * @return true if this object is different and needs updating
	 */
	public boolean isDifferentFromGscfJson( jsonObject ) {
		return this.name != jsonObject.name || this.sampleToken != jsonObject.sampleToken
	}
}
