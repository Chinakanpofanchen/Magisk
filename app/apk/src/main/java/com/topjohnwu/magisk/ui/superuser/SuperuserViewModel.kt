package com.topjohnwu.magisk.ui.superuser

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.AppContext
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
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    @get:Bindable
    var showSystemApps = false
        set(value) = set(value, field, { field = it }, BR.showSystemApps) {
            doQuery(query)
        }

    @get:Bindable
    var query = ""
        set(value) = set(value, field, { field = it }, BR.query) {
            doQuery(value)
        }

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        if (!Info.showSuperUser) {
            loading = false
            return
        }
        loading = true
        withContext(Dispatchers.IO) {
            db.deleteOutdated()
            db.delete(AppContext.applicationInfo.uid)

            // Build a map of existing policies by UID
            val policyMap = db.fetchAll().associateBy { it.uid }

            // Get all installed applications
            val pm = AppContext.packageManager
            val packages = pm.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES)
                .asFlow()
                .filter { it.uid != AppContext.applicationInfo.uid }

            // Create PolicyRvItem for each app
            val policies = packages.mapNotNull { appInfo ->
                val packageName = appInfo.packageName

                // Check if there's an existing policy for this UID
                val existingPolicy = policyMap[appInfo.uid]
                val policy = existingPolicy ?: SuPolicy(
                    uid = appInfo.uid,
                    policy = SuPolicy.QUERY
                )

                try {
                    val info = pm.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
                    PolicyRvItem(
                        this@SuperuserViewModel, policy,
                        info.packageName,
                        info.sharedUserId != null,
                        info.applicationInfo?.loadIcon(pm) ?: pm.defaultActivityIcon,
                        info.applicationInfo?.getLabel(pm) ?: info.packageName
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }.toCollection(ArrayList<PolicyRvItem>())

            // Sort by app name
            policies.sortWith(compareBy(
                { it.appName.lowercase(Locale.ROOT) },
                { it.packageName }
            ))
            itemsPolicies.set(policies)
        }
        doQuery(query)
        loading = false
    }

    private fun doQuery(s: String) {
        itemsPolicies.filter {
            fun filterSystem() = showSystemApps || !it.isSystemApp()

            fun filterQuery(): Boolean {
                fun inName() = it.appName.contains(s, ignoreCase = true)
                fun inPackage() = it.packageName.contains(s, ignoreCase = true)
                return inName() || inPackage()
            }

            filterSystem() && filterQuery()
        }
    }

    private fun PolicyRvItem.isSystemApp(): Boolean {
        return try {
            val pm = AppContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
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
                val res = if (policy >= SuPolicy.ALLOW) R.string.su_snack_grant else R.string.su_snack_deny
                item.item.policy = policy
                db.update(item.item)
                SnackbarEvent(res.asText(item.appName)).publish()
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            updateState()
        }
    }
}
