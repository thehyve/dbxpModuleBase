package org.dbxp.moduleBase

class AssayService {
    static transactional = 'mongo'

    List getAssaysReadableByUser(User user) {
        def readEnabledAuthorizations = Auth.findAllByUserAndCanRead(user, true)
		def assays = new ArrayList()

		// find assays
		readEnabledAuthorizations*.study.assays.flatten().find{ it != null }.each {
			assays[ assays.size() ] = it
		}

		return assays
    }

    Map getAssaysReadableByUserAndGroupedByStudy(User user) {
		def assays = getAssaysReadableByUser(user)

		return (assays) ? assays.groupBy { Assay assay -> assay.study } : [:]
    }
}