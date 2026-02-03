package io.github.vvb2060.magisk.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.magisk.BR
import io.github.vvb2060.magisk.arch.BaseViewModel
import io.github.vvb2060.magisk.core.AppContext
import io.github.vvb2060.magisk.core.BuildConfig
import io.github.vvb2060.magisk.core.Config
import io.github.vvb2060.magisk.core.Const
import io.github.vvb2060.magisk.core.Info
import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.core.isRunningAsStub
import io.github.vvb2060.magisk.core.ktx.activity
import io.github.vvb2060.magisk.core.ktx.toast
import io.github.vvb2060.magisk.core.tasks.AppMigration
import io.github.vvb2060.magisk.core.utils.LocaleSetting
import io.github.vvb2060.magisk.core.utils.RootUtils
import io.github.vvb2060.magisk.databinding.bindExtra
import io.github.vvb2060.magisk.events.AddHomeIconEvent
import io.github.vvb2060.magisk.events.AuthEvent
import io.github.vvb2060.magisk.events.SnackbarEvent
import kotlinx.coroutines.launch

class SettingsViewModel : BaseViewModel(), BaseSettingsItem.Handler {

    val items = createItems()
    val extraBindings = bindExtra {
        it.put(BR.handler, this)
    }

    private fun createItems(): List<BaseSettingsItem> {
        val context = AppContext
        val hidden = context.packageName != BuildConfig.APP_PACKAGE_NAME

        // Customization
        val list = mutableListOf(
            Customization,
            Theme, if (LocaleSetting.useLocaleManager) LanguageSystem else Language
        )
        if (isRunningAsStub && ShortcutManagerCompat.isRequestPinShortcutSupported(context))
            list.add(AddShortcut)

        // Manager
        list.addAll(listOf(
            AppSettings,
            UpdateChannel, UpdateChannelUrl, DoHToggle, UpdateChecker, DownloadPath, RandNameToggle
        ))
        if (Info.env.isActive && Const.USER_ID == 0) {
            if (hidden) list.add(Restore) else list.add(Hide)
        }

        // Magisk
        if (Info.env.isActive) {
            list.addAll(listOf(
                Magisk,
                SystemlessHosts
            ))
            if (Const.Version.atLeast_24_0()) {
                list.addAll(listOf(Zygisk, DenyList, DenyListConfig))
            }
        }

        // Superuser
        if (Info.showSuperUser) {
            list.addAll(listOf(
                Superuser,
                Tapjack, Authentication, AccessMode, MultiuserMode, MountNamespaceMode,
                AutomaticResponse, RequestTimeout, SUNotification
            ))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Re-authenticate is not feasible on 8.0+
                list.add(Reauthenticate)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Can hide overlay windows on 12.0+
                list.remove(Tapjack)
            }
            if (Const.Version.atLeast_30_1()) {
                list.add(Restrict)
            }
        }

        return list
    }

    override fun onItemPressed(view: View, item: BaseSettingsItem, doAction: () -> Unit) {
        when (item) {
            DownloadPath -> withExternalRW(doAction)
            UpdateChecker -> withPostNotificationPermission(doAction)
            Authentication -> AuthEvent(doAction).publish()
            AutomaticResponse -> if (Config.suAuth) AuthEvent(doAction).publish() else doAction()
            else -> doAction()
        }
    }

    override fun onItemAction(view: View, item: BaseSettingsItem) {
        when (item) {
            Theme -> SettingsFragmentDirections.actionSettingsFragmentToThemeFragment().navigate()
            LanguageSystem -> launchAppLocaleSettings(view.activity)
            AddShortcut -> AddHomeIconEvent().publish()
            SystemlessHosts -> createHosts()
            DenyListConfig -> SettingsFragmentDirections.actionSettingsFragmentToDenyFragment().navigate()
            UpdateChannel -> openUrlIfNecessary(view)
            is Hide -> viewModelScope.launch { AppMigration.hide(view.activity, item.value) }
            Restore -> viewModelScope.launch { AppMigration.restore(view.activity) }
            Zygisk -> if (Zygisk.mismatch) SnackbarEvent(R.string.reboot_apply_change).publish()
            else -> Unit
        }
    }

    private fun launchAppLocaleSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }

    private fun openUrlIfNecessary(view: View) {
        UpdateChannelUrl.refresh()
        if (UpdateChannelUrl.isEnabled && UpdateChannelUrl.value.isBlank()) {
            UpdateChannelUrl.onPressed(view, this)
        }
    }

    private fun createHosts() {
        viewModelScope.launch {
            RootUtils.addSystemlessHosts()
            AppContext.toast(R.string.settings_hosts_toast, Toast.LENGTH_SHORT)
        }
    }
}
