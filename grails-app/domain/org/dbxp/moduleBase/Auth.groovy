package org.dbxp.moduleBase

import java.io.Serializable;

/**
 * This class provides the kind of authorization a user has on a specific study
 * 
 * @author robert
 *
 */
class Auth implements Serializable {
	boolean canRead
	boolean canWrite
	boolean isOwner
	
	static belongsTo = [ study: Study, user: User ]
	
    static constraints = {
    }
	
	static Auth authorization( Study study, User user ) {
		return Auth.findByStudyAndUser( study, user );
	}
	
	static Auth createAuth( Study study, User user ) {
		Auth a = new Auth( canRead: false, canWrite: false, isOwner: false );
		study.addToAuth( a );
		user.addToAuth( a );
		a.save();
	}
	
	public String toString() {
		return "Auth: " + id + ": " + user + "/" + study + ": canRead: " + canRead + "/canWrite: " + canWrite + "/isOwner: " + isOwner
	}
}
