package com.wa2c.android.cifsdocumentsprovider.common.values

/**
 * Storage type
 */
enum class StorageType(
    val value: String,
) {
    /** JCIFS-NG */
    JCIFS("JCIFS"),
    /** SMBJ */
    SMBJ("SMBJ"),
    ;

    companion object {
        val default: StorageType = JCIFS
    }
}
