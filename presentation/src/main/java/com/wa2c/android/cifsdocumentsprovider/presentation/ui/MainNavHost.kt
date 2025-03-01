package com.wa2c.android.cifsdocumentsprovider.presentation.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.edit.EditScreen
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.folder.FolderScreen
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.home.HomeScreen
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.host.HostScreen
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.receive.ReceiveFile
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.send.SendScreen
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.send.SendViewModel
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.settings.SettingsScreen

/**
 * Main nav host
 */
@Composable
internal fun MainNavHost(
    navController: NavHostController,
    sendViewModel: SendViewModel,
    onOpenFile: (List<Uri>) -> Unit,
    onCloseApp: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = HomeScreenName,
    ) {
        // Navigate SendScreen if sending
        if (sendViewModel.sendDataList.value.isNotEmpty()) {
            navController.navigate(route = SendScreenName, navOptions = navOptions {
                this.popUpTo(SendScreenName)
            })
            return@NavHost
        }

        // Home Screen
        composable(
            route = HomeScreenName,
        ) {
            HomeScreen(
                onOpenFile = { uris ->
                    onOpenFile(uris)
                },
                onNavigateSettings = {
                    navController.navigate(route = SettingsScreenName)
                },
                onNavigateEdit = { connection ->
                    navController.navigate(route = EditScreenRouteName + "?$EditScreenParamId=${connection.id}")
                },
                onNavigateHost = {
                    navController.navigate(route = HostScreenName)
                }
            )
        }

        // Edit Screen
        composable(
            route = "$EditScreenRouteName?$EditScreenParamHost={$EditScreenParamHost}&$EditScreenParamId={$EditScreenParamId}",
            arguments = listOf(
                navArgument(EditScreenParamHost) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(EditScreenParamId) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            // BackStack
            val selectedHost =
                backStackEntry.savedStateHandle.getStateFlow<String?>(HostScreenResultKey, null)
                    .collectAsState(null).value?.also {
                    backStackEntry.savedStateHandle.remove<String?>(HostScreenResultKey)
                }
            val selectedUri =
                backStackEntry.savedStateHandle.getStateFlow<Uri?>(FolderScreenResultKey, null)
                    .collectAsState(null).value?.also {
                    backStackEntry.savedStateHandle.remove<Uri?>(FolderScreenResultKey)
                }

            EditScreen(
                selectedHost = selectedHost,
                selectedUri = selectedUri,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateSearchHost = {
                    navController.navigate(route = "$HostScreenName?$HostScreenParamId=${it?.id ?: ""}")
                },
                onNavigateSelectFolder = {
                    navController.navigate(route = FolderScreenName)
                },
            )
        }

        // Host Screen
        composable(
            route = "$HostScreenName?$HostScreenParamId={$HostScreenParamId}",
            arguments = listOf(
                navArgument(HostScreenParamId) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            HostScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSelectItem = { host ->
                    if (backStackEntry.arguments?.getString(HostScreenParamId) == null) {
                        navController.navigate(
                            route = EditScreenRouteName + "?host=${host}",
                            navOptions = navOptions {
                                this.popUpTo(HomeScreenName)
                            })
                    } else {
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            HostScreenResultKey,
                            host
                        )
                        navController.popBackStack()
                    }
                },
                onSetManually = {
                    navController.navigate(route = EditScreenRouteName, navOptions = navOptions {
                        this.popUpTo(HomeScreenName)
                    })
                }
            )
        }

        // Folder Screen
        composable(
            route = FolderScreenName,
        ) {
            FolderScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateSet = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(FolderScreenResultKey, it)
                    navController.popBackStack()
                }
            )
        }

        // Settings Screen
        composable(
            route = SettingsScreenName,
        ) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Send Screen
        composable(
            route = SendScreenName,
        ) {
            SendScreen(
                viewModel = sendViewModel,
                onNavigateFinish = {
                    onCloseApp()
                }
            )
        }

        // Receive File
        composable(
            route = ReceiveFileName,
            deepLinks = listOf(
                navDeepLink { action = Intent.ACTION_SEND },
                navDeepLink { action = Intent.ACTION_SEND_MULTIPLE },
            )
        ) { backStackEntry ->
            @Suppress("DEPRECATION")
            val uriList = remember {
                backStackEntry.arguments?.getParcelable<Intent>(NavController.KEY_DEEP_LINK_INTENT)
                    ?.let { intent ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            (intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                                ?.let { listOf(it) })
                                ?: (intent.getParcelableArrayExtra(
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java
                                )?.toList())
                        } else {
                            (intent.getParcelableExtra<Uri?>(Intent.EXTRA_STREAM)
                                ?.let { listOf(it) })
                                ?: (intent.getParcelableArrayListExtra<Uri?>(Intent.EXTRA_STREAM)
                                    ?.toList())
                        }
                    }
            } ?: emptyList()

            ReceiveFile(
                uriList = uriList,
                onNavigateFinish = {
                    navController.navigate(route = SendScreenName, navOptions = navOptions {
                        this.popUpTo(SendScreenName)
                    })
                }
            ) {
                navController.navigate(route = SendScreenName, navOptions = navOptions {
                    this.popUpTo(SendScreenName)
                })
                sendViewModel.sendUri(sourceUriList = uriList, targetUri = it)
            }
        }
    }
}

private const val HomeScreenName = "home"
private const val EditScreenRouteName = "edit"
private const val HostScreenName = "host"
private const val FolderScreenName = "folder"
private const val SettingsScreenName = "settings"
private const val SendScreenName = "send"
private const val ReceiveFileName = "receive"

private const val HostScreenResultKey = "host_result"
private const val FolderScreenResultKey = "folder_result"

const val HostScreenParamId = "id"
const val EditScreenParamHost = "host"
const val EditScreenParamId = "id"
