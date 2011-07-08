package org.dbxp.moduleBase

class LogoutController {
	def gscfService;
	
	@NoAuthenticationRequired
	def index = {
		if( session.user ) {
			//clear user info from session
			session.user = null
			log.info("Session.User is now ${session.user}")
			session.sessionToken = null
			log.info("Session.sessionToken is now ${session.sessionToken}")
			
			//logout on GSCF side, and do not redirect back to this module (but stay within GSCF after logout)
			def redirectURL = gscfService.urlLogoutRemote( params, session.sessionToken );
			log.info("Redirecting to: ${redirectURL}")
			
			redirect(url: redirectURL)
			return false
		} else {
			log.info( "User is not logged in while trying to logout" );
			// If the user is not logged in, we don't have to log him out
			// Redirect to homepage
			redirect( url: g.resource( 'dir': '' ) );
		}
	}
}
