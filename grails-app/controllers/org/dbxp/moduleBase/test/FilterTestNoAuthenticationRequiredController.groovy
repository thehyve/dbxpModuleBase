package org.dbxp.moduleBase.test

import org.dbxp.moduleBase.NoAuthenticationRequired
import org.dbxp.moduleBase.AuthenticationRequired

@NoAuthenticationRequired
class FilterTestNoAuthenticationRequiredController {

    @AuthenticationRequired
    def actionAuthenticationRequired = {

        render 'bad'

    }

    @NoAuthenticationRequired
    def actionNoAuthenticationRequired = {

        render 'good'

    }

    def actionNoAnnotation = {

        render 'good'

    }

}
