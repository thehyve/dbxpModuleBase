package org.dbxp.moduleBase.test

import org.dbxp.moduleBase.AuthenticationRequired
import org.dbxp.moduleBase.NoAuthenticationRequired

@AuthenticationRequired
class FilterTestAuthenticationRequiredController {

    @AuthenticationRequired
    def actionAuthenticationRequired = {

        render 'bad'

    }

    @NoAuthenticationRequired
    def actionNoAuthenticationRequired = {

        render 'good'

    }

    def actionNoAnnotation = {

        render 'bad'

    }

}
