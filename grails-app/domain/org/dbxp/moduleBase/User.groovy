package org.dbxp.moduleBase

/*
 * User is used to track the activity of a user within the Metagenomics Module
 * And data uploaded is linked to the user that provided the data
 */
class User implements Serializable {
	
    Long identifier // ID of GSCF user
    String username   // Username of GSCF user
	Boolean isAdministrator

	static hasMany = [ auth: Auth ]
	
	static constraints = {
		identifier(nullable:true);
	}
	
	static mapping = {
		table 'gscfuser'
		auth cascade: "all-delete-orphan"
	}
	
	public String toString() {
		return "User " + id + ": " + ( username ?: "" )
	}
	
	public boolean equals( Object o ) {
		if( o == null )
			return false
		
		if( o instanceof User ) {
			User other = (User) o;
			
			// If anything is null, return false
			if( other.identifier == null || other.username == null || this.identifier == null || this.username == null )
				return false;
				
			return (other.identifier == this.identifier && other.username == this.username);
		} else {
			return false
		}
	}
	
	public boolean canRead( Study study ) {
		Auth authorization = auth.find { it.study.equals( study ) }
		
		if( !authorization )
			return false
		
		return authorization.canRead
	}
	
	public boolean canWrite( Study study ) {
		Auth authorization = auth.find { it.study.equals( study ) }

		if( !authorization )
			return false
		
		return authorization.canWrite
	}

	public boolean isOwner( Study study ) {
		Auth authorization = auth.find { it.study.equals( study ) }
		
		if( !authorization )
			return false
		
		return authorization.isOwner
	}
	

}
