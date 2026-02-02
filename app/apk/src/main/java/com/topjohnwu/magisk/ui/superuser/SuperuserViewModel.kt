package com.topjohnwu.magisk.ui.superuser

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.utils.RootUtils
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R
import com.topjohnwu.magisk.core.data.magiskdb.PolicyDao
import com.topjohnwu.magisk.core.ktx.getLabel
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.databinding.MergeObservableList
import com.topjohnwu.magisk.databinding.RvItem
import com.topjohnwu.magisk.databinding.bindExtra
import com.topjohnwu.magisk.databinding.filterList
import com.topjohnwu.magisk.databinding.set
import com.topjohnwu.magisk.dialog.SuperuserRevokeDialog
import com.topjohnwu.magisk.events.AuthEvent
import com.topjohnwu.magisk.events.SnackbarEvent
import com.topjohnwu.magisk.utils.asText
import com.topjohnwu.magisk.view.TextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SuperuserViewModel(
    private val db: PolicyDao
) : AsyncLoadViewModel() {

    private val itemNoData = TextItem(R.string.superuser_policy_none)

    private val itemsHelpers = ObservableArrayList<TextItem>()
    private val itemsPolicies = filterList<PolicyRvItem>(viewModelScope)

    val items = MergeObservableList<RvItem>()
        .insertList(itemsHelpers)
        .insertList(itemsPolicies)
    val extraBindings = bindExtra {
        it.put(BR.listener, this)
        it.put(BR.viewModel, this)
    }

    @get:Bindable
    var loading = false
        private set(value) = set(value, field, { field = it }, BR.loading)

    @get:Bindable
    var permissionRequired = false
        private set(value) = set(value, field, { field = it }, BR.permissionRequired)

    @get:Bindable
    var query = ""
        set(value) = set(value, field, { field = it }, BR.query) {
            doQuery(value)
        }

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        if (!Info.showSuperUser) {
            return
        }

        permissionRequired = false
        loading = true
        withContext(Dispatchers.IO) {
            db.deleteOutdated()
            db.delete(AppContext.applicationInfo.uid)

            // Fetch all authorization records from database
            val policyMap = db.fetchAll().associateBy { it.uid }
            val pm = AppContext.packageManager

            // Try to get package list via root service first (if available and rooted)
            val packageNames = if (Info.isRooted) {
                try {
                    RootUtils.getInstalledPackages()
                } catch (e: Exception) {
                    // Fall back to standard method if root method fails
                    null
                }
            } else null

            // Get all third-party apps
            val policyItems = if (packageNames != null) {
                // Use root method result - convert package names to ApplicationInfo
                packageNames.asFlow().mapNotNull { packageName ->
                    try {
                        pm.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                }
            } else {
                // Fall back to standard method and check permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val granted = AppContext.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        permissionRequired = true
                        loading = false
                        return@withContext
                    }
                }
                pm.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES).asFlow()
            }
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .mapNotNull { appInfo ->
                    try {
                        val packageName = appInfo.packageName
                        val info: android.content.pm.PackageInfo = pm.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
                        val applicationInfo = info.applicationInfo ?: return@mapNotNull null

                        // Get existing policy or create new one with QUERY status
                        val policy = policyMap[applicationInfo.uid] ?: SuPolicy(
                            uid = applicationInfo.uid,
                            policy = SuPolicy.QUERY
                        )

                        PolicyRvItem(
                            this@SuperuserViewModel, policy,
                            info.packageName,
                            info.sharedUserId != null,
                            applicationInfo.loadIcon(pm),
                            applicationInfo.getLabel(pm) ?: packageName
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                }.toCollection(ArrayList<PolicyRvItem>())

            // Sort: ALLOW apps first, DENY/QUERY apps after, then by app name
            policyItems.sortWith(compareBy(
                { it.item.policy == SuPolicy.QUERY },  // QUERY last
                { it.item.policy != SuPolicy.ALLOW },  // ALLOW first
                { it.appName.lowercase(Locale.ROOT) },
                { it.packageName }
            ))
            itemsPolicies.set(policyItems)
        }
        doQuery(query)
        loading = false
    }

    private fun doQuery(s: String) {
        itemsPolicies.filter {
            fun inName() = it.appName.contains(s, ignoreCase = true)
            fun inPackage() = it.packageName.contains(s, ignoreCase = true)
            inName() || inPackage()
        }
    }

    // ---

    fun deletePressed(item: PolicyRvItem) {
        fun updateState() = viewModelScope.launch {
            db.delete(item.item.uid)
            doLoadWork()
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            SuperuserRevokeDialog(item.title) { updateState() }.show()
        }
    }

    fun updateNotify(item: PolicyRvItem) {
        viewModelScope.launch {
            db.update(item.item)
            val res = when {
                item.item.notification -> R.string.su_snack_notif_on
                else -> R.string.su_snack_notif_off
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun updateLogging(item: PolicyRvItem) {
        viewModelScope.launch {
            db.update(item.item)
            val res = when {
                item.item.logging -> R.string.su_snack_log_on
                else -> R.string.su_snack_log_off
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun updatePolicy(item: PolicyRvItem, policy: Int) {
        fun updateState() {
            viewModelScope.launch {
                if (policy >= SuPolicy.ALLOW) {
                    // Grant: update or create policy
                    item.item.policy = policy
                    db.update(item.item)
                    SnackbarEvent(R.string.su_snack_grant.asText(item.appName)).publish()
                } else {
                    // Deny: delete the policy completely
                    db.delete(item.item.uid)
                    SnackbarEvent(R.string.su_snack_deny.asText(item.appName)).publish()
                    // Reload to show updated list
                    doLoadWork()
                }
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            updateState()
        }
    }

    fun requestPermission() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: open app settings page
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${AppContext.packageName}")
            }
        } else {
            // Android 10 and below: open app info settings
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${AppContext.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        AppContext.startActivity(intent)
    }
}
