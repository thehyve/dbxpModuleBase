package org.dbxp.moduleBase

import java.io.Serializable;

/**
* Minimal representation of a sample. The sampleToken is the link with a sample object in GSCF.
*
* @see GscfService.getSample
*/
class Sample implements Serializable {
	String  sampleToken		// Unique within an assay
	String  name
	String  subject
	String  event
	
	static belongsTo    = [ assay: Assay ]

	static mapping = {
		columns {
			assayToken index:'sampletoken_idx'
		}
	}
	static constraints = {
		subject(nullable: true)
		event(nullable: true)
	}
	
	public String token() { return sampleToken; }

	public String toString() {
		return "Sample " + id + ": " + ( name ?: "" )
	}
}
