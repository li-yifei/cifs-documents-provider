package com.wa2c.android.cifsdocumentsprovider.data.storage.smbj

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import com.hierynomus.smbj.share.File
import com.wa2c.android.cifsdocumentsprovider.common.utils.logD
import com.wa2c.android.cifsdocumentsprovider.common.values.AccessMode
import com.wa2c.android.cifsdocumentsprovider.common.utils.BackgroundBufferReader
import com.wa2c.android.cifsdocumentsprovider.common.utils.processFileIo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

/**
 * Proxy File Callback for SMBJ (Buffering IO)
 */
class SmbjProxyFileCallback(
    private val file: File,
    private val mode: AccessMode,
    private val onFileRelease: () -> Unit,
) : ProxyFileDescriptorCallback(), CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.IO + Job()

    /** File size */
    private val fileSize: Long by lazy { file.fileInformation.standardInformation.endOfFile }

    private val readerLazy = lazy {
        BackgroundBufferReader(fileSize) { start, array, off, len ->
            file.read(array, start, off, len)
        }
    }

    private val reader: BackgroundBufferReader get() = readerLazy.value

    @Throws(ErrnoException::class)
    override fun onGetSize(): Long {
        return fileSize
    }

    @Throws(ErrnoException::class)
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        return processFileIo(coroutineContext) {
            reader.readBuffer(offset, size, data)
        }
    }

    @Throws(ErrnoException::class)
    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        if (mode != AccessMode.W) { throw ErrnoException("Writing is not permitted", OsConstants.EBADF) }
        return processFileIo(coroutineContext) {
            file.writeAsync(data, offset, 0, size)
            size
        }
    }
    
    @Throws(ErrnoException::class)
    override fun onFsync() {
        // Nothing to do
    }

    @Throws(ErrnoException::class)
    override fun onRelease() {
        logD("onRelease: ${file.uncPath}")
        processFileIo(coroutineContext) {
            logD("release begin")
            if (readerLazy.isInitialized()) {
                reader.close()
            }
            onFileRelease()
            logD("release end")
        }
    }

}