package org.dbxp.moduleBase

class AssayService {
    static transactional = 'mongo'

	Assay getAssayReadableByUserById(User user, AssayId = null) {
		return (AssayId == null) ?: getAssaysReadableByUser(user).find { it.id as int == AssayId as int }
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
        authorizations*.study.assays.flatten().findAll { it != null }
    }

    Map getAssaysReadableByUserAndGroupedByStudy(User user) {
		def assays = getAssaysReadableByUser(user)

		return (assays) ? assays.groupBy { Assay assay -> assay.study } : [:]
    }
}