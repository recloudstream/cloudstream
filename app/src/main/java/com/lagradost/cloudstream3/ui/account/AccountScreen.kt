package com.lagradost.cloudstream3.ui.account

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.Account
import com.lagradost.cloudstream4.compose.ActionDialog
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.BlackTextField
import com.lagradost.cloudstream4.compose.EMULATOR
import com.lagradost.cloudstream4.compose.PHONE
import com.lagradost.cloudstream4.compose.RoundedShape
import com.lagradost.cloudstream4.compose.TV
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.compose.circle
import com.lagradost.cloudstream4.compose.circleBorder
import com.lagradost.cloudstream4.compose.focusOutline
import com.lagradost.cloudstream4.compose.isLayout
import com.lagradost.cloudstream4.compose.rounded
import com.lagradost.cloudstream4.compose.whiteOutline
import com.lagradost.cloudstream4.state.SearchableData
import com.lagradost.cloudstream4.state.sortByInt
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.mihon.material.padding
import com.mihon.presentation.settings.widget.SwitchPreferenceWidget
import com.mihon.presentation.settings.widget.TextPreferenceWidget
import kotlinx.collections.immutable.toPersistentMap

@PreviewLightDark
@Composable
fun Preview() {
    CloudStreamPreviewTheme {
        AccountScreen.Content(
            state = AccountState(
                canEditAccount = false,
                editAccount = Account(
                    keyIndex = 1,
                    name = "Hello world",
                    defaultImageIndex = 2,
                    lockPin = "1234"
                ),
                accounts = SearchableData.from(
                    data =
                        (0..7).associateWith { index ->
                            Account(
                                keyIndex = index,
                                name = "hello $index",
                                defaultImageIndex = index,
                                lockPin = if (index % 2 == 1) {
                                    "1234"
                                } else {
                                    null
                                }
                            )
                        }.toPersistentMap(),
                    sortedBy = sortByInt { it.keyIndex },
                ),
            ), onAction = {})
    }
}

object AccountScreen {
    @Composable
    fun Content(
        state: AccountState,
        onAction: (AccountAction) -> Unit
    ) {
        if (state.editAccount != null) {
            EditAccountDialog(
                exists = state.accounts.data.containsKey(state.editAccount.keyIndex),
                account = state.editAccount,
                onAction = onAction
            )
        }

        Scaffold { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.End
            ) {
                val (edit, accounts) = FocusRequester.createRefs()

                IconButton(
                    onClick = {
                        onAction(AccountAction.SetEditMode(!state.canEditAccount))
                    }, modifier = Modifier
                        .focusRequester(edit)
                        .focusProperties {
                            down = accounts
                        }
                        .focusOutline(shape = CircleShape)
                ) {
                    Icon(
                        painter = if (state.canEditAccount) {
                            painterResource(R.drawable.close_24px)
                        } else {
                            painterResource(R.drawable.edit_24px)
                        },
                        contentDescription = stringResource(R.string.manage_accounts)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (state.canEditAccount) stringResource(R.string.manage_accounts) else stringResource(
                            R.string.select_an_account
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(MaterialTheme.padding.large)
                    )

                    AccountsRow(
                        state = state, onAction = onAction,
                        modifier = Modifier
                            .focusRequester(accounts)
                            .focusProperties {
                                up = edit
                            })

                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    enum class WaitForDialogType {
        RemoveLock,
        NewPassword,
        DeleteAccount,
        NewProfileUrl,
        NewProfilePicture,
        Login,
    }

    @Composable
    fun WaitForDialog(
        type: WaitForDialogType,
        onDismissRequest: () -> Unit,
        account: Account,
        onAction: (AccountAction) -> Unit
    ) {
        when (type) {
            WaitForDialogType.RemoveLock -> {
                if (account.lockPin != null) {
                    EnterPinDialog(
                        expected = account.lockPin,
                        onDismissRequest = onDismissRequest,
                        onCorrect = {
                            onAction(AccountAction.EditAccount(account.copy(lockPin = null)))
                        }
                    )
                }
            }

            WaitForDialogType.Login -> {
                if (account.lockPin != null) {
                    EnterPinDialog(
                        expected = account.lockPin,
                        onDismissRequest = onDismissRequest,
                        onCorrect = {
                            onAction(AccountAction.SaveAccount(account))
                            onAction(AccountAction.EditAccount(null))
                        }
                    )
                }
            }

            WaitForDialogType.NewPassword -> {
                SetPinDialog(
                    onDismissRequest = onDismissRequest,
                    onSet = { newLockPin ->
                        onAction(AccountAction.EditAccount(account.copy(lockPin = newLockPin)))
                    })
            }

            WaitForDialogType.DeleteAccount -> {
                ActionDialog(
                    title = stringResource(R.string.delete),
                    text = stringResource(R.string.delete_message, account.name),
                    confirm = {
                        onAction(AccountAction.DeleteAccount(account.keyIndex))
                        onAction(AccountAction.EditAccount(null))
                        onDismissRequest()
                    },
                    dismiss = onDismissRequest,
                    confirmText = stringResource(R.string.delete),
                    dismissText = stringResource(R.string.cancel)
                )
            }

            WaitForDialogType.NewProfilePicture -> {
                SetPictureDialog(
                    account = account,
                    onDismissRequest = onDismissRequest,
                    onSet = { newImage ->
                        onAction(
                            AccountAction.EditAccount(
                                account.copy(
                                    defaultImageIndex = newImage,
                                    customImage = null
                                )
                            )
                        )
                        onDismissRequest()
                    })
            }

            WaitForDialogType.NewProfileUrl -> {
                SetUrlDialog(onDismissRequest = onDismissRequest, onSet = { newImage ->
                    onAction(AccountAction.EditAccount(account.copy(customImage = newImage)))
                    onDismissRequest()
                })
            }
        }
    }

    @Composable
    fun EditAccountDialog(
        exists: Boolean,
        account: Account,
        onAction: (AccountAction) -> Unit
    ) {
        val selectFileSelector =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                // It lies, it can be null if file manager quits.
                if (uri == null) return@rememberLauncherForActivityResult
                val context = CloudStreamApp.context ?: return@rememberLauncherForActivityResult

                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    onAction(AccountAction.EditAccount(account = account.copy(customImage = uri.toString())))
                } catch (t: Throwable) {
                    logError(t)
                }
            }

        var pendingDialog by remember { mutableStateOf<WaitForDialogType?>(null) }
        pendingDialog?.let { waitType ->
            WaitForDialog(
                type = waitType,
                onDismissRequest = { pendingDialog = null },
                account = account,
                onAction = onAction
            )
        }

        val (focusRequester) = FocusRequester.createRefs()
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        val isTv = isLayout(TV)
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            confirmButton = {
                if (exists) {
                    WhiteButton(text = stringResource(R.string.delete)) {
                        pendingDialog = WaitForDialogType.DeleteAccount
                    }
                }
                WhiteButton(text = stringResource(R.string.sort_apply)) {
                    if (account.lockPin == null) {
                        onAction(AccountAction.SaveAccount(account))
                        onAction(AccountAction.EditAccount(null))
                    } else {
                        pendingDialog = WaitForDialogType.Login
                    }
                }
            },
            dismissButton = {
                BlackButton(
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    onAction(AccountAction.EditAccount(null))
                }
            },
            onDismissRequest = {
                /**
                 * For UX reasons we can only dismiss it with the cancel button,
                 * but does not have those issues
                 * */
                if (isTv) {
                    onAction(AccountAction.EditAccount(null))
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    AccountImage(
                        selected = false,
                        edit = false,
                        account = account,
                        onClick = {
                            onAction(
                                AccountAction.EditAccount(
                                    account.copy(
                                        customImage = null,
                                        defaultImageIndex = (account.defaultImageIndex + 1) % DataStoreHelper.profileImages.size
                                    )
                                )
                            )
                        },
                        onLongClick = {})
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                    BlackTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = account.name,
                        onValueChange = { newText ->
                            val newName = if (newText.length <= 100) {
                                newText
                            } else {
                                newText.substring(0, 100)
                            }
                            onAction(
                                AccountAction.EditAccount(
                                    account.copy(
                                        name = newName
                                    )
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.PersonName,
                        ),
                        placeHolder = stringResource(R.string.name)
                    )

                    var selectProfileImage by remember { mutableStateOf(false) }


                    TextPreferenceWidget(
                        modifier = Modifier
                            .rounded()
                            .focusOutline(),
                        title = stringResource(R.string.edit_profile_image_title),
                        widget = {
                            Icon(
                                painter = painterResource(R.drawable.add_photo_alternate_24px),
                                contentDescription = null
                            )
                            DropdownMenu(expanded = selectProfileImage, onDismissRequest = {
                                selectProfileImage = false
                            }, shape = RoundedShape()) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.edit_profile_image_url)) },
                                    onClick = {
                                        pendingDialog = WaitForDialogType.NewProfileUrl
                                        selectProfileImage = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.edit_profile_image_picker)) },
                                    onClick = {
                                        selectFileSelector.launch(arrayOf("image/*"))
                                        selectProfileImage = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.edit_profile_image_default)) },
                                    onClick = {
                                        pendingDialog = WaitForDialogType.NewProfilePicture
                                        selectProfileImage = false
                                    }
                                )
                            }
                        },
                        onPreferenceClick = {
                            selectProfileImage = true
                        }
                    )

                    if (account.keyIndex != 0) {
                        SwitchPreferenceWidget(
                            modifier = Modifier
                                .rounded()
                                .focusOutline(),
                            title = stringResource(R.string.lock_profile),
                            checked = account.lockPin != null,
                            onCheckedChanged = {
                                pendingDialog = if (account.lockPin == null) {
                                    WaitForDialogType.NewPassword
                                } else {
                                    WaitForDialogType.RemoveLock
                                }
                            })
                    }

                }
            }
        )
    }


    @Composable
    fun AccountsRow(
        state: AccountState,
        onAction: (AccountAction) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        FlowRow(
            modifier = modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(
                MaterialTheme.padding.medium,
                alignment = Alignment.Start
            ),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.padding.medium,
                alignment = Alignment.CenterVertically
            ),
        ) {
            state.accounts.sorted.forEach { key ->
                val account = state.accounts.data[key]!!
                Account(
                    edit = state.canEditAccount,
                    account = account,
                    onAction = onAction,
                    selected = state.lastLoginKeyIndex == account.keyIndex
                )
            }

            var hasFocus by remember { mutableStateOf(false) }
            val size = 100.dp
            val innerPadding by animateDpAsState(
                targetValue = if (hasFocus || isLayout(PHONE or EMULATOR)) 0.dp else MaterialTheme.padding.extraSmall,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(size)
                    .onFocusChanged { newFocus ->
                        hasFocus = newFocus.hasFocus
                    }
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .clickable(interactionSource = interactionSource, indication = null) {
                            onAction(AccountAction.NewAccount)
                        }
                        .size(size)
                        .padding(innerPadding)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .circle()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .whiteOutline(hasFocus = hasFocus, shape = CircleShape)
                        .indication(
                            interactionSource = interactionSource,
                            indication = ripple()
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add_24px),
                        contentDescription = stringResource(R.string.add_account),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.height(MaterialTheme.padding.small))
                Text(
                    text = stringResource(R.string.add_account),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    @Composable
    fun PinTextField(value: String, onValueChange: (String) -> Unit) {
        BlackTextField(
            value = value, keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.NumberPassword,
            ), placeHolder = stringResource(R.string.pin), onValueChange = onValueChange
        )
    }

    @Composable
    fun EnterPinDialog(expected: String, onDismissRequest: (() -> Unit), onCorrect: (() -> Unit)) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = onDismissRequest,
            confirmButton = {},
            dismissButton = {
                BlackButton(text = stringResource(R.string.cancel), onClick = onDismissRequest)
            },
            text = {
                var inputText by remember { mutableStateOf("") }
                var hasError by remember { mutableStateOf(false) }
                Column {
                    PinTextField(
                        value = inputText,
                        onValueChange = { newText ->
                            if (newText.length == expected.length) {
                                if (newText == expected) {
                                    onCorrect()
                                    onDismissRequest()
                                } else {
                                    inputText = ""
                                    hasError = true
                                }
                            } else {
                                inputText = newText
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
                    if (hasError) {
                        Text(text = stringResource(R.string.pin_error_incorrect))
                    }
                }
            },
            title = {
                Text(text = stringResource(R.string.enter_pin))
            })
    }

    @Composable
    fun SetPinDialog(onDismissRequest: (() -> Unit), onSet: ((String) -> Unit)) {
        var inputText by remember { mutableStateOf("") }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = onDismissRequest,
            confirmButton = {
                if (inputText.length == 4) {
                    WhiteButton(
                        text = stringResource(R.string.sort_apply),
                        onClick = {
                            onSet(inputText)
                            onDismissRequest()
                        })
                }
            },
            dismissButton = {
                BlackButton(text = stringResource(R.string.cancel), onClick = onDismissRequest)
            },
            text = {
                PinTextField(
                    value = inputText,
                    onValueChange = { newText ->
                        /**
                         * We require isDigit for SetPin,
                         * but do not for the EnterPin to avoid inaccessable accounts
                         * */
                        val newTextFilter = if (newText.all(Char::isDigit)) {
                            newText
                        } else {
                            newText.filter(Char::isDigit)
                        }

                        inputText = if (newTextFilter.length <= 4) {
                            newTextFilter
                        } else {
                            newTextFilter.substring(0, 4)
                        }
                    },
                )
            },
            title = {
                Text(text = stringResource(R.string.enter_pin))
            })
    }

    @Composable
    fun SetUrlDialog(onDismissRequest: (() -> Unit), onSet: ((String) -> Unit)) {
        var inputText by remember { mutableStateOf("") }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = onDismissRequest,
            confirmButton = {
                WhiteButton(
                    text = stringResource(R.string.sort_apply),
                    onClick = {
                        onSet(inputText)
                        onDismissRequest()
                    })
            },
            dismissButton = {
                BlackButton(text = stringResource(R.string.cancel), onClick = onDismissRequest)
            },
            text = {
                BlackTextField(
                    value = inputText,
                    onValueChange = { newText ->
                        inputText = newText
                    },
                    placeHolder = stringResource(R.string.edit_profile_image_hint)
                )
            },
            title = {
                Text(text = stringResource(R.string.edit_profile_image_title))
            })
    }

    @Composable
    fun SetPictureDialog(
        account: Account,
        onDismissRequest: (() -> Unit),
        onSet: ((Int) -> Unit)
    ) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = onDismissRequest,
            confirmButton = { },
            dismissButton = {
                BlackButton(text = stringResource(R.string.cancel), onClick = onDismissRequest)
            },
            text = {
                FlowRow(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.medium,
                        alignment = Alignment.Start
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.medium,
                        alignment = Alignment.CenterVertically
                    ),
                ) {
                    for (i in DataStoreHelper.profileImages.indices) {
                        AccountImage(
                            edit = false,
                            showName = false,
                            selected = account.defaultImageIndex == i,
                            account = account.copy(
                                customImage = null,
                                defaultImageIndex = i,
                                lockPin = null
                            ),
                            onClick = {
                                onSet(i)
                            },
                            onLongClick = {})
                    }
                }
            },
            title = {
                Text(text = stringResource(R.string.edit_profile_image_title))
            })
    }

    @Composable
    fun AccountImage(
        /** If we should show the edit pen */
        edit: Boolean,
        /** If this element should have focus */
        selected: Boolean,
        /** If we should show the name under the picture */
        showName: Boolean = true,
        account: Account,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val image = account.customImage
            ?: DataStoreHelper.profileImages[account.defaultImageIndex % DataStoreHelper.profileImages.size]

        val interactionSource = remember { MutableInteractionSource() }
        var hasFocus by remember { mutableStateOf(false) }
        val size = 100.dp
        val innerPadding by animateDpAsState(
            targetValue = if (hasFocus || isLayout(PHONE or EMULATOR)) 0.dp else MaterialTheme.padding.extraSmall,
        )
        val (focusRequester) = FocusRequester.createRefs()
        LaunchedEffect(Unit) {
            if (selected) {
                focusRequester.requestFocus()
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(size)
                .focusRequester(focusRequester)
                .onFocusChanged { newFocus ->
                    hasFocus = newFocus.hasFocus
                }
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = image,
                    modifier = Modifier
                        .circleBorder(size)
                        .whiteOutline(hasFocus = hasFocus, shape = CircleShape)
                        .indication(interactionSource = interactionSource, indication = ripple()),
                    contentDescription = account.name,
                    contentScale = ContentScale.Crop,
                    alpha = if (edit) {
                        0.6f
                    } else {
                        1.0f
                    }
                )

                if (account.lockPin != null) {
                    Box(
                        contentAlignment = Alignment.TopEnd, modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                MaterialTheme.padding.extraSmall
                            )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.video_locked),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(22.dp + MaterialTheme.padding.extraSmall * 2)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                )
                                .padding(MaterialTheme.padding.extraSmall)
                        )
                    }
                }

                this@Column.AnimatedVisibility(
                    visible = edit,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.edit_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            if (showName) {
                Spacer(Modifier.height(MaterialTheme.padding.small))
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

        }
    }

    @Composable
    fun Account(
        edit: Boolean,
        selected: Boolean,
        account: Account,
        onAction: (AccountAction) -> Unit
    ) {
        var waitForLock by remember { mutableStateOf(false) }
        if (waitForLock && account.lockPin != null) {
            EnterPinDialog(
                expected = account.lockPin,
                onDismissRequest = {
                    waitForLock = false
                }, onCorrect = {
                    onAction(AccountAction.LoginWithAccount(account.keyIndex))
                })
        }

        AccountImage(selected = selected, edit = edit, account = account, onClick = {
            if (edit) {
                onAction(AccountAction.EditAccount(account))
            } else if (account.lockPin != null) {
                waitForLock = true
            } else {
                onAction(AccountAction.LoginWithAccount(account.keyIndex))
            }
        }, onLongClick = {
            /** Long click -> edit without having to press the edit button for better UX */
            onAction(AccountAction.EditAccount(account))
        })
    }
}