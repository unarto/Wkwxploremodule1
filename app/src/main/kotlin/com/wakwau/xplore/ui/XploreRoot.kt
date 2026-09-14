// [Modul: :app] [Jalur Class]: app/src/main/kotlin/com/wakwau/xplore/ui/XploreRoot.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API

package com.wakwau.xplore.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wakwau.xplore.XploreApplication
import com.wakwau.xplore.core.utils.ui.components.AppDialog
import com.wakwau.xplore.di.appOrchestrator
import com.wakwau.xplore.di.dualPaneViewModelFactory
import com.wakwau.xplore.di.treeNavigationAdapter
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.ui.presentation.DualPaneViewModel
import com.wakwau.xplore.filemanager.ui.screen.DualPaneFileManagerScreen
import com.wakwau.xplore.filemanager.ui.state.FileDialogUiState
import com.wakwau.xplore.fileoperations.ui.dialog.ConflictResolutionDialog
import com.wakwau.xplore.fileoperations.ui.dialog.DeleteConfirmationDialog
import com.wakwau.xplore.fileoperations.ui.dialog.ProgressDialog
import com.wakwau.xplore.fileoperations.ui.dialog.RenameDialog
import com.wakwau.xplore.fileoperations.ui.state.OperationUiState
import com.wakwau.xplore.navigation.AppRoute
import com.wakwau.xplore.orchestrator.AppOrchestratorViewModel
import com.wakwau.xplore.search.ui.FileSearchDialog
import com.wakwau.xplore.settings.ui.engine.SettingsViewModel
import com.wakwau.xplore.settings.ui.screen.SettingsTreeScreen

@Composable
fun XploreRoot(
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as XploreApplication
    val compositionRoot = app.appCompositionRoot

    val appOrchestrator: AppOrchestratorViewModel = compositionRoot.appOrchestrator

    val dualPaneViewModel: DualPaneViewModel = viewModel(
        factory = compositionRoot.dualPaneViewModelFactory
    )
    val dualPaneState by dualPaneViewModel.state.collectAsStateWithLifecycle()
    val dualPaneDialogUiState by dualPaneViewModel.dialogUiState.collectAsStateWithLifecycle()
    val opState by appOrchestrator.operationState.collectAsStateWithLifecycle()
    val searchUiState by appOrchestrator.searchUiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()

    // [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/ui/XploreRoot.kt
    // [Penjelasan]: Perbaikan alur pemicu izin berdasarkan izinpenyimpanan.md, inisialisasi peluncur intent & observer siklus hidup
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        dualPaneViewModel.checkPermission()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                dualPaneViewModel.checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/ui/XploreRoot.kt
    // [Penjelasan]: Menampilkan feedback Toast instan kepada pengguna saat operasi I/O berhasil (misal: buat folder baru) atau gagal, mencegah silent failure.
    androidx.compose.runtime.LaunchedEffect(opState) {
        when (val state = opState) {
            is OperationUiState.Success -> {
                android.widget.Toast.makeText(context, context.getString(state.messageRes), android.widget.Toast.LENGTH_SHORT).show()
                dualPaneViewModel.dispatch(DualPaneEvent.ClearOperationState)
            }
            is OperationUiState.Failure -> {
                val errorMsg = when (state.errorMessage) {
                    com.wakwau.xplore.core.storage.operation.FileOperationError.ACCESS_DENIED.name -> context.getString(com.wakwau.xplore.filemanager.ui.R.string.err_access_denied)
                    com.wakwau.xplore.core.storage.operation.FileOperationError.NOT_FOUND.name -> context.getString(com.wakwau.xplore.filemanager.ui.R.string.err_not_found)
                    com.wakwau.xplore.core.storage.operation.FileOperationError.ALREADY_EXISTS.name -> context.getString(com.wakwau.xplore.filemanager.ui.R.string.err_already_exists)
                    com.wakwau.xplore.core.storage.operation.FileOperationError.INVALID_NAME.name -> context.getString(com.wakwau.xplore.filemanager.ui.R.string.err_invalid_name)
                    com.wakwau.xplore.core.storage.operation.FileOperationError.INVALID_LOCATION.name -> context.getString(com.wakwau.xplore.filemanager.ui.R.string.err_invalid_location)
                    com.wakwau.xplore.core.storage.operation.FileOperationError.IO_ERROR.name -> context.getString(com.wakwau.xplore.filemanager.ui.R.string.err_io_error)
                    com.wakwau.xplore.core.storage.operation.FileOperationError.NOT_SUPPORTED.name -> context.getString(com.wakwau.xplore.filemanager.ui.R.string.err_not_supported)
                    else -> state.errorMessage
                }
                android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.DualPane.route
    ) {
        composable(AppRoute.DualPane.route) {
            // [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/ui/XploreRoot.kt
            // [Penjelasan]: Perbaikan alur pemicu izin berdasarkan izinpenyimpanan.md, menampilkan PermissionScreen jika hasPermission == false
            if (!dualPaneState.hasPermission) {
                com.wakwau.xplore.filemanager.ui.screen.PermissionScreen(
                    onRequestPermission = {
                        com.wakwau.xplore.filemanager.ui.permission.PermissionIntentHelper.requestStorageAccess(
                            context = context,
                            onLaunchLegacy = { permissions ->
                                permissionLauncher.launch(permissions)
                            }
                        )
                    }
                )
            } else {
                DualPaneFileManagerScreen(
                    viewModel = dualPaneViewModel,
                    treeAdapter = compositionRoot.treeNavigationAdapter,
                    storageVolumes = dualPaneState.storageVolumes,
                    onSettingsClick = { navController.navigate(AppRoute.Settings.route) },
                    onRemoveLinkClick = { uri -> dualPaneViewModel.removeLinkedStorage(uri) },
                searchDialog = {
                    if (searchUiState.isSearchDialogOpen) {
                        FileSearchDialog(
                            state = searchUiState,
                            currentLocation = dualPaneState.activePanel.currentLocation,
                            onDismiss = { dualPaneViewModel.dispatch(DualPaneEvent.DismissSearchDialog) },
                            onSearch = { query ->
                                dualPaneViewModel.executeSearch(query)
                            },
                            onCancelSearch = { dualPaneViewModel.dispatch(DualPaneEvent.SearchCancelled) },
                            onClearHistory = { dualPaneViewModel.dispatch(DualPaneEvent.ClearSearchHistory()) },
                            onFileClick = { item ->
                                dualPaneViewModel.onSearchItemClick(item)
                            }
                        )
                    }
                },
                operationDialogs = {
                    when (val currentOpState = opState) {
                        is OperationUiState.ConflictResolution -> {
                            ConflictResolutionDialog(
                                conflict = currentOpState.currentConflict,
                                currentIndex = currentOpState.currentConflictIndex,
                                totalConflicts = currentOpState.pendingConflicts.size,
                                onDecision = { choice, applyToAll ->
                                    val conflict = currentOpState.currentConflict ?: return@ConflictResolutionDialog
                                    val map = mutableMapOf(conflict.source to choice)
                                    if (applyToAll) {
                                        for (i in currentOpState.currentConflictIndex until currentOpState.pendingConflicts.size) {
                                            map[currentOpState.pendingConflicts[i].source] = choice
                                        }
                                    }
                                    if (currentOpState.isMove) {
                                        appOrchestrator.resolveMoveConflict(currentOpState.allSources, currentOpState.destinationDir, map)
                                    } else {
                                        appOrchestrator.resolveCopyConflict(currentOpState.allSources, currentOpState.destinationDir, map)
                                    }
                                },
                                onDismiss = { dualPaneViewModel.dispatch(DualPaneEvent.ClearOperationState) }
                            )
                        }
                        is OperationUiState.Running -> {
                            ProgressDialog(
                                operationName = stringResource(currentOpState.operationNameRes),
                                progress = currentOpState.progress,
                                onCancel = { dualPaneViewModel.dispatch(DualPaneEvent.CancelOperationRequested) }
                            )
                        }
                        is OperationUiState.Confirming -> {
                            val isMove = currentOpState.isMove
                            val opName = if (isMove) {
                                stringResource(com.wakwau.xplore.filemanager.ui.R.string.label_move)
                            } else {
                                stringResource(com.wakwau.xplore.filemanager.ui.R.string.cd_copy)
                            }

                            AppDialog(
                                title = stringResource(com.wakwau.xplore.filemanager.ui.R.string.title_operation_items, opName),
                                confirmButtonText = opName,
                                onConfirm = {
                                    if (isMove) {
                                        dualPaneViewModel.dispatch(DualPaneEvent.ExecuteConfirmedMove(currentOpState.items, currentOpState.targetPath))
                                    } else {
                                        dualPaneViewModel.dispatch(DualPaneEvent.ExecuteConfirmedCopy(currentOpState.items, currentOpState.targetPath))
                                    }
                                },
                                onDismissRequest = { dualPaneViewModel.dispatch(DualPaneEvent.ClearOperationState) }
                            ) {
                                Text(
                                    text = stringResource(
                                        com.wakwau.xplore.filemanager.ui.R.string.msg_operation_confirmation,
                                        opName, currentOpState.items.size, currentOpState.targetPath
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        is OperationUiState.Failure -> {
                            // [Modul: :app] [Jalur Class]: app/src/main/kotlin/com/wakwau/xplore/ui/XploreRoot.kt
                            // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
                            val errorText = when (currentOpState.errorMessage) {
                                com.wakwau.xplore.core.storage.operation.FileOperationError.ACCESS_DENIED.name -> stringResource(com.wakwau.xplore.filemanager.ui.R.string.err_access_denied)
                                com.wakwau.xplore.core.storage.operation.FileOperationError.NOT_FOUND.name -> stringResource(com.wakwau.xplore.filemanager.ui.R.string.err_not_found)
                                com.wakwau.xplore.core.storage.operation.FileOperationError.ALREADY_EXISTS.name -> stringResource(com.wakwau.xplore.filemanager.ui.R.string.err_already_exists)
                                com.wakwau.xplore.core.storage.operation.FileOperationError.INVALID_NAME.name -> stringResource(com.wakwau.xplore.filemanager.ui.R.string.err_invalid_name)
                                com.wakwau.xplore.core.storage.operation.FileOperationError.INVALID_LOCATION.name -> stringResource(com.wakwau.xplore.filemanager.ui.R.string.err_invalid_location)
                                com.wakwau.xplore.core.storage.operation.FileOperationError.IO_ERROR.name -> stringResource(com.wakwau.xplore.filemanager.ui.R.string.err_io_error)
                                com.wakwau.xplore.core.storage.operation.FileOperationError.NOT_SUPPORTED.name -> stringResource(com.wakwau.xplore.filemanager.ui.R.string.err_not_supported)
                                else -> currentOpState.errorMessage
                            }
                            if (currentOpState.errorMessage == com.wakwau.xplore.core.storage.operation.FileOperationError.ACCESS_DENIED.name) {
                                AppDialog(
                                    title = stringResource(com.wakwau.xplore.filemanager.ui.R.string.title_processing_operation),
                                    confirmButtonText = stringResource(com.wakwau.xplore.filemanager.ui.R.string.btn_grant_permission),
                                    onConfirm = {
                                        dualPaneViewModel.dispatch(DualPaneEvent.ClearOperationState)
                                        com.wakwau.xplore.filemanager.ui.permission.PermissionIntentHelper.requestStorageAccess(
                                            context = context,
                                            onLaunchLegacy = { permissions ->
                                                permissionLauncher.launch(permissions)
                                            }
                                        )
                                    },
                                    dismissButtonText = stringResource(com.wakwau.xplore.filemanager.ui.R.string.cd_close),
                                    onDismissRequest = { dualPaneViewModel.dispatch(DualPaneEvent.ClearOperationState) }
                                ) {
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                AppDialog(
                                    title = stringResource(com.wakwau.xplore.filemanager.ui.R.string.title_processing_operation),
                                    confirmButtonText = stringResource(com.wakwau.xplore.filemanager.ui.R.string.cd_close),
                                    onConfirm = { dualPaneViewModel.dispatch(DualPaneEvent.ClearOperationState) },
                                    dismissButtonText = null,
                                    onDismissRequest = { dualPaneViewModel.dispatch(DualPaneEvent.ClearOperationState) }
                                ) {
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        else -> {}
                    }

                    when (val dialogState = dualPaneDialogUiState) {
                        is FileDialogUiState.RenameItem -> {
                            RenameDialog(
                                initialName = dialogState.item.name,
                                onConfirm = { newName ->
                                    dualPaneViewModel.dispatch(DualPaneEvent.RenameItem(dialogState.item, newName))
                                },
                                onDismissRequest = { dualPaneViewModel.dispatch(DualPaneEvent.DismissInputDialog) }
                            )
                        }
                        is FileDialogUiState.DeleteConfirmation -> {
                            DeleteConfirmationDialog(
                                itemCount = dialogState.items.size,
                                onConfirm = {
                                    dualPaneViewModel.dispatch(DualPaneEvent.DeleteSelected(dialogState.items))
                                },
                                onDismissRequest = { dualPaneViewModel.dispatch(DualPaneEvent.DismissInputDialog) }
                            )
                        }
                        else -> {}
                    }
                }
            )
            }
        }

        composable(AppRoute.Settings.route) {
            SettingsTreeScreen(
                settingsState = settingsState,
                onThemeSelected = { mode -> settingsViewModel.setThemeMode(mode) },
                onLanguageSelected = { lang -> settingsViewModel.setLanguage(lang) },
                onFileSystemAccessModeSelected = { mode -> settingsViewModel.setFileSystemAccessMode(mode) },
                onRootReadOnlyChanged = { isReadOnly -> settingsViewModel.setRootReadOnly(isReadOnly) },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
