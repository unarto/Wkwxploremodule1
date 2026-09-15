package com.wakwau.xplore.fileoperations.conflict

class UnresolvedTransferConflicts(val conflicts: List<FileConflict>) :
    IllegalStateException("Transfer requires explicit conflict decisions")
