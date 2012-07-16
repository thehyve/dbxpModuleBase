package org.dbxp.moduleBase

import org.springframework.transaction.annotation.Transactional

// Set all methods to read-only transactional
@Transactional(readOnly = true)
class AssayService {

	Assay getAssayReadableByUserById(User user, assayId = null) {
		getAssaysReadableByUser(user).find { it.id == assayId as Long }
	}
    
    List getAssaysReadableByUser(User user) {
        def readEnabledAuthorizations = Auth.findAllByUserAndCanRead(user, true)

        getAssaysFromAuthorizations(readEnabledAuthorizations)
    }

    List getAssaysWritableByUser(User user) {
        def writeEnabledAuthorizations = Auth.findAllByUserAndCanWrite(user, true)

		getAssaysFromAuthorizations(writeEnabledAuthorizations)
    }

    List getAssaysFromAuthorizations(authorizations) {
        authorizations*.study.sort { it.name }*.assays.flatten().findAll { it != null }.sort { it.name }
    }

    Map getAssaysReadableByUserAndGroupedByStudy(User user) {
		def assays = getAssaysReadableByUser(user)

		return assays ? assays.groupBy { Assay assay -> assay.study } : [:]
    }
}