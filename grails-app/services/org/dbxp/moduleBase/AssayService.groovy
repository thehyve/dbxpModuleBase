package org.dbxp.moduleBase

class AssayService {

    static transactional = true

    /**
     * ...
     * Only studies with at least one readable assay are returned.
     *
     * @param user
     * @return Map with study objects as keys and assay object lists as values
     *  e.g.: [Study1: [assay1, assay2], Study2: [assay3, assay4]]
     */
    List getStudiesAndAssaysReadableByUser(User user) {

        // TODO: is 'read' flag always enabled when 'write' or 'isOwner' is?
        def authorizations = Auth.findAllByUserAndCanRead(user, true)

        def studiesWithAssays =  authorizations*.study.findAll { it.assays }

        studiesWithAssays.collect { study -> [study: study.assays] }

    }

}
