package org.dbxp.moduleBase

@NoAuthenticationRequired
class CssController {

    /**
     * Renders the css file, while providing gsp methods inside the 
     * css file.
     */
	def module = {
		response.setContentType( "text/css" );
		render( view: "module.css" );	
	}
	
}
