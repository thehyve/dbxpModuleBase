package org.dbxp.moduleBase.test

import org.dbxp.moduleBase.AuthenticationRequired
import org.dbxp.moduleBase.NoAuthenticationRequired


class FilterTestNoAnnotationController {

    @AuthenticationRequired
    def actionAuthenticationRequired = {

        render 'bad'

    }

    @NoAuthenticationRequired
    def actionNoAuthenticationRequired = {

        render 'good'

    }

    def actionNoAnnotation = {

        if (    grailsApplication.config.module.defaultAuthenticationRequired == null ||
                grailsApplication.config.module.defaultAuthenticationRequired) {
            render 'bad'
        } else render 'good'

    }

}
