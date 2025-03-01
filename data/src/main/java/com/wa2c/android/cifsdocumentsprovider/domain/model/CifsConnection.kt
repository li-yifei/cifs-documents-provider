package com.wa2c.android.cifsdocumentsprovider.domain.model

import android.os.Parcelable
import com.wa2c.android.cifsdocumentsprovider.common.utils.getSmbUri
import com.wa2c.android.cifsdocumentsprovider.common.values.StorageType
import com.wa2c.android.cifsdocumentsprovider.common.values.USER_GUEST
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * CIFS Connection
 */
@Parcelize
@Serializable
data class CifsConnection(
    val id: String,
    val name: String,
    val storage: StorageType = StorageType.default,
    val domain: String?,
    val host: String,
    val port: String?,
    val enableDfs: Boolean,
    val folder: String?,
    val user: String?,
    val password: String?,
    val anonymous: Boolean,
    val extension: Boolean,
    val safeTransfer: Boolean,
): Parcelable, java.io.Serializable {

    /** True if new item. */
    val isNew: Boolean
        get() = (id == NEW_ID)

    val isAnonymous: Boolean
        get() = anonymous

    val isGuest: Boolean
        get() = user.isNullOrEmpty() || user.equals(USER_GUEST, ignoreCase = true)

    /** Root SMB URI (smb://) */
    val rootSmbUri: String
        get() = getSmbUri(host, port, null, true)

    /** Folder SMB URI (smb://) */
    val folderSmbUri: String
        get() = getSmbUri(host, port, folder, true)

    companion object {

        const val NEW_ID = ""

        fun CifsConnection.toDto(inputUri: String?): com.wa2c.android.cifsdocumentsprovider.data.storage.interfaces.StorageConnection {
            return com.wa2c.android.cifsdocumentsprovider.data.storage.interfaces.StorageConnection(
                id = id,
                name = name,
                storage = storage,
                domain = domain,
                host = host,
                port = port,
                enableDfs = enableDfs,
                folder = folder,
                user = user,
                password = password,
                anonymous = anonymous,
                extension = extension,
                safeTransfer = safeTransfer,

                inputUri = inputUri,
            )
        }


        /**
         * Create from host
         */
        fun createFromHost(hostText: String): CifsConnection {
            return CifsConnection(
                id = NEW_ID,
                name = hostText,
                storage = StorageType.default,
                domain = null,
                host = hostText,
                port = null,
                enableDfs = false,
                folder = null,
                user = null,
                password = null,
                anonymous = false,
                extension = false,
                safeTransfer = false,
            )
        }

    }
}
