package org.dbxp.moduleBase

class AssayService {

    static transactional = true

    List getAssaysReadableByUser(User user) {

        def readEnabledAuthorizations = Auth.findAllByUserAndCanRead(user, true)

        readEnabledAuthorizations*.study.assays.flatten()

    }

    Map getAssaysReadableByUserAndGroupedByStudy(User user) {

        getAssaysReadableByUser(user).groupBy { Assay assay -> assay.study }

    }
}
