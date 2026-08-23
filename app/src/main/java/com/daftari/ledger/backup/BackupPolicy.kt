package com.daftari.ledger.backup

object BackupPolicy {
    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun hasDiverged(settings: GoogleBackupSettings, existing: List<RemoteBackup>): Boolean {
        if (settings.lastRemoteId.isBlank()) return false
        val canonicalHead = existing
            .filter { it.datasetId == settings.datasetId && !it.conflict }
            .maxByOrNull(RemoteBackup::createdAt)
        return canonicalHead != null && canonicalHead.id != settings.lastRemoteId
    }

    fun deletionCandidates(all: List<RemoteBackup>, settings: GoogleBackupSettings): List<RemoteBackup> {
        val own = all.filter { it.deviceId == settings.deviceId }.sortedByDescending { it.createdAt }
        val protected = own.filter { it.conflict }.mapTo(mutableSetOf()) { it.id }
        own.firstOrNull()?.let { protected += it.id }
        own.take(settings.keepDaily).forEach { protected += it.id }
        own.drop(settings.keepDaily)
            .groupBy { it.createdAt / WEEK_MILLIS }
            .values
            .mapNotNull { period -> period.maxByOrNull(RemoteBackup::createdAt) }
            .sortedByDescending(RemoteBackup::createdAt)
            .take(settings.keepWeekly)
            .forEach { protected += it.id }
        return own.filterNot { it.id in protected }
    }
}
