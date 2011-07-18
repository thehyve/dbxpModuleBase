package org.dbxp.moduleBase

class AssayService {

    static transactional = true

    List getAssaysReadableByUser(User user) {

        // TODO: is 'read' flag always enabled when 'write' or 'isOwner' is?
        def readEnabledAuthorizations = Auth.findAllByUserAndCanRead(user, true)

        readEnabledAuthorizations*.study.assays.flatten()

    }

    Map getAssaysReadableByUserAndGroupedByStudy(User user) {

        getAssaysReadableByUser(user).groupBy { Assay assay -> assay.study }

    }
}
