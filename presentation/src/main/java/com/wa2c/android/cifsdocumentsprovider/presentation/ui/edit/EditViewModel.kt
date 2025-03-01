package com.wa2c.android.cifsdocumentsprovider.presentation.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wa2c.android.cifsdocumentsprovider.common.values.ConnectionResult
import com.wa2c.android.cifsdocumentsprovider.common.values.StorageType
import com.wa2c.android.cifsdocumentsprovider.domain.model.CifsConnection
import com.wa2c.android.cifsdocumentsprovider.domain.repository.CifsRepository
import com.wa2c.android.cifsdocumentsprovider.presentation.ext.MainCoroutineScope
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.EditScreenParamHost
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.EditScreenParamId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Edit Screen ViewModel
 */
@HiltViewModel
class EditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val cifsRepository: CifsRepository
) : ViewModel(), CoroutineScope by MainCoroutineScope() {

    private val paramId: String? = savedStateHandle[EditScreenParamId]
    private val paramHost: String? = savedStateHandle[EditScreenParamHost]

    init {
        launch {
            val connection = paramId?.let {
                cifsRepository.getConnection(paramId).also { initConnection = it }
            } ?: CifsConnection.createFromHost(paramHost ?: "")
            deployCifsConnection(connection)
        }
    }

    private val _navigateSearchHost = MutableSharedFlow<Result<CifsConnection>>()
    val navigateSearchHost = _navigateSearchHost.asSharedFlow()

    private val _navigateSelectFolder = MutableSharedFlow<Result<CifsConnection>>()
    val navigateSelectFolder = _navigateSelectFolder.asSharedFlow()

    private val _result = MutableSharedFlow<Result<Unit>>()
    val result = _result.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    var name = MutableStateFlow<String?>(null)
    var storage = MutableStateFlow<StorageType>(StorageType.default)
    var domain = MutableStateFlow<String?>(null)
    var host = MutableStateFlow<String?>(null)
    var port = MutableStateFlow<String?>(null)
    var enableDfs = MutableStateFlow<Boolean>(false)
    var folder = MutableStateFlow<String?>(null)
    var user = MutableStateFlow<String?>(null)
    var password = MutableStateFlow<String?>(null)
    var anonymous = MutableStateFlow<Boolean>(false)

    var extension = MutableStateFlow<Boolean>(false)
    var safeTransfer = MutableStateFlow<Boolean>(false)

    private val _connectionResult = MutableSharedFlow<ConnectionResult?>()
    val connectionResult = channelFlow<ConnectionResult?> {
        launch { _connectionResult.collect { send(it) } }
        launch { storage.collect { send(null) } }
        launch { domain.collect { send(null) } }
        launch { host.collect { send(null) } }
        launch { port.collect { send(null) } }
        launch { enableDfs.collect { send(null) } }
        launch { folder.collect { send(null) } }
        launch { user.collect { send(null) } }
        launch { password.collect { send(null) } }
        launch { anonymous.collect { send(null) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Current ID */
    private var currentId: String = CifsConnection.NEW_ID

    /** True if adding new settings */
    val isNew: Boolean
        get() = currentId == CifsConnection.NEW_ID

    /** True if data changed */
    val isChanged: Boolean
        get() = initConnection == null || initConnection != createCifsConnection(false)

    /** Init connection */
    private var initConnection: CifsConnection? = null

    /**
     * Deploy connection data.
     */
    private fun deployCifsConnection(connection: CifsConnection?) {
        currentId = connection?.id ?: CifsConnection.NEW_ID
        name.value = connection?.name
        storage.value = connection?.storage ?: StorageType.default
        domain.value = connection?.domain
        host.value = connection?.host
        port.value = connection?.port
        enableDfs.value = connection?.enableDfs ?: false
        folder.value = connection?.folder
        user.value = connection?.user
        password.value = connection?.password
        anonymous.value = connection?.anonymous ?: false
        extension.value = connection?.extension ?: false
        safeTransfer.value = connection?.safeTransfer ?: false
    }

    /**
     * Create connection data
     */
    private fun createCifsConnection(generateId: Boolean): CifsConnection? {
        val isAnonymous = anonymous.value
        return CifsConnection(
            id = if (generateId) UUID.randomUUID().toString() else currentId,
            name = name.value?.ifEmpty { null } ?: host.value ?: return null,
            storage = storage.value,
            domain = domain.value?.ifEmpty { null },
            host = host.value?.ifEmpty { null } ?: return null,
            port = port.value?.ifEmpty { null },
            enableDfs = enableDfs.value,
            folder = folder.value?.ifEmpty { null },
            user = if (isAnonymous) null else user.value?.ifEmpty { null },
            password = if (isAnonymous) null else password.value?.ifEmpty { null },
            anonymous = isAnonymous,
            extension = extension.value,
            safeTransfer = safeTransfer.value,
        )
    }

    /**
     * Check connection
     */
    fun onClickCheckConnection() {
        launch {
            _isBusy.emit(true)
            runCatching {
                _connectionResult.emit(null)
               createCifsConnection(false)?.let { cifsRepository.checkConnection(it) }
            }.fold(
                onSuccess = { _connectionResult.emit(it ?: ConnectionResult.Failure()) },
                onFailure = { _connectionResult.emit(ConnectionResult.Failure(it)) }
            ).also {
                _isBusy.emit(false)
            }
        }
    }

    fun onClickSearchHost() {
        launch {
            val result = createCifsConnection(false)?.let {
                Result.success(it)
            } ?: let {
                Result.failure(Exception())
            }
            _navigateSearchHost.emit(result)
        }
    }

    /**
     * Select Folder Click
     */
    fun onClickSelectFolder() {
        launch {
            _isBusy.emit(true)
            runCatching {
                val folderConnection = createCifsConnection(false) ?: throw IOException()
                val result = cifsRepository.checkConnection(folderConnection)
                if (result !is ConnectionResult.Failure) {
                    cifsRepository.saveTemporaryConnection(folderConnection)
                    _navigateSelectFolder.emit(Result.success(folderConnection))
                } else {
                    _connectionResult.emit(result)
                }
            }.onFailure {
                _connectionResult.emit(ConnectionResult.Failure(cause = it))
            }.also {
                _isBusy.emit(false)
            }
        }
    }

    /**
     * Delete Click
     */
    fun onClickDelete() {
        launch {
            _isBusy.emit(true)
            runCatching {
                cifsRepository.deleteConnection(currentId)
            }.onSuccess {
                _result.emit(Result.success(Unit))
                _isBusy.emit(false)
            }.onFailure {
                _result.emit(Result.failure(it))
                _isBusy.emit(false)
            }
        }
    }

    /**
     * Save Click
     */
    fun onClickSave() {
        launch {
            _isBusy.emit(true)
            runCatching {
                createCifsConnection(isNew)?.let { con ->
                    if (cifsRepository.loadConnection().filter { it.id != con.id }
                            .any { it.folderSmbUri == con.folderSmbUri }) {
                        // Duplicate URI
                        throw IllegalArgumentException()
                    }
                    cifsRepository.saveConnection(con)
                    currentId = con.id
                    initConnection = con
                } ?: throw IOException()
            }.onSuccess {
                _result.emit(Result.success(Unit))
                _isBusy.emit(false)
            }.onFailure {
                _result.emit(Result.failure(it))
                _isBusy.emit(false)
            }
        }
    }

    override fun onCleared() {
        runBlocking {
            cifsRepository.saveTemporaryConnection(null)
        }
        super.onCleared()
    }
}
