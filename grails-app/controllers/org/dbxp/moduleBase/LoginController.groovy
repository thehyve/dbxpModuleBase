package org.dbxp.moduleBase

class LoginController {
	// Make sure the user logs in by setting the annotation. After
	// logging in, the user will be redirected to the home page of the module
	@AuthenticationRequired
	def index = {
		redirect( url: g.resource( 'dir': '' ) );
	}
}
