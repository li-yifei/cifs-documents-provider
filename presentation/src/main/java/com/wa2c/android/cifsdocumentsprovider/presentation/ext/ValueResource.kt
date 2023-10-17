package com.wa2c.android.cifsdocumentsprovider.presentation.ext

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.wa2c.android.cifsdocumentsprovider.common.values.ConnectionResult
import com.wa2c.android.cifsdocumentsprovider.common.values.HostSortType
import com.wa2c.android.cifsdocumentsprovider.common.values.Language
import com.wa2c.android.cifsdocumentsprovider.common.values.SendDataState
import com.wa2c.android.cifsdocumentsprovider.common.values.StorageType
import com.wa2c.android.cifsdocumentsprovider.common.values.UiTheme
import com.wa2c.android.cifsdocumentsprovider.presentation.R
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.common.PopupMessageType


/** ConnectionResult type */
val ConnectionResult.messageType: PopupMessageType
    get() = when (this) {
        is ConnectionResult.Success -> PopupMessageType.Success
        is ConnectionResult.Warning -> PopupMessageType.Warning
        is ConnectionResult.Failure -> PopupMessageType.Error
    }

/** ConnectionResult message string resource ID */
val ConnectionResult.messageRes
    @StringRes
    get() = when (this) {
        is ConnectionResult.Success -> { R.string.edit_check_connection_ok_message }
        is ConnectionResult.Warning -> { R.string.edit_check_connection_wn_message }
        is ConnectionResult.Failure -> { R.string.edit_check_connection_ng_message }
    }

val HostSortType.labelRes: Int
    @StringRes
    get() = when (this) {
        HostSortType.DetectionAscend -> R.string.host_sort_type_detection_ascend
        HostSortType.DetectionDescend -> R.string.host_sort_type_detection_descend
        HostSortType.HostNameAscend -> R.string.host_sort_type_host_name_ascend
        HostSortType.HostNameDescend -> R.string.host_sort_type_host_name_descend
        HostSortType.IpAddressAscend -> R.string.host_sort_type_ip_address_ascend
        HostSortType.IpAddressDescend -> R.string.host_sort_type_ip_address_descend
    }

fun UiTheme.getLabel(context: Context): String {
    return when (this) {
        UiTheme.DEFAULT -> R.string.enum_theme_default
        UiTheme.LIGHT -> R.string.enum_theme_light
        UiTheme.DARK -> R.string.enum_theme_dark
    }.let {
        context.getString(it)
    }
}

val UiTheme.mode: Int
    get() = when(this) {
        UiTheme.DEFAULT -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        UiTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        UiTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }

fun Language.getLabel(context: Context): String {
    return when (this) {
        Language.ENGLISH -> R.string.enum_language_en
        Language.JAPANESE-> R.string.enum_language_ja
        Language.SLOVAK -> R.string.enum_language_sk
        Language.CHINESE -> R.string.enum_language_zh_rcn
    }.let {
        context.getString(it)
    }
}

/** SendDataState string resource ID */
val SendDataState.labelRes: Int
    @StringRes
    get() = when (this) {
        SendDataState.READY -> R.string.send_state_ready
        SendDataState.CONFIRM -> R.string.send_state_overwrite
        SendDataState.OVERWRITE -> R.string.send_state_overwrite
        SendDataState.PROGRESS -> R.string.send_state_cancel
        SendDataState.SUCCESS -> R.string.send_state_success
        SendDataState.FAILURE -> R.string.send_state_failure
        SendDataState.CANCEL -> R.string.send_state_cancel
    }

val StorageType.labelRes: Int
    @StringRes
    get() = when (this) {
        StorageType.JCIFS -> R.string.enum_storage_jcifsng
        StorageType.SMBJ -> R.string.enum_storage_smbj
    }