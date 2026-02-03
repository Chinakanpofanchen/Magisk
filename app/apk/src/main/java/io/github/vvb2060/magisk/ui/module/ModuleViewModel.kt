package io.github.vvb2060.magisk.ui.module

import android.net.Uri
import androidx.databinding.Bindable
import androidx.lifecycle.MutableLiveData
import io.github.vvb2060.magisk.BR
import io.github.vvb2060.magisk.MainDirections
import io.github.vvb2060.magisk.R
import io.github.vvb2060.magisk.arch.AsyncLoadViewModel
import io.github.vvb2060.magisk.core.Const
import io.github.vvb2060.magisk.core.Info
import io.github.vvb2060.magisk.core.base.ContentResultCallback
import io.github.vvb2060.magisk.core.model.module.LocalModule
import io.github.vvb2060.magisk.core.model.module.OnlineModule
import io.github.vvb2060.magisk.databinding.MergeObservableList
import io.github.vvb2060.magisk.databinding.RvItem
import io.github.vvb2060.magisk.databinding.bindExtra
import io.github.vvb2060.magisk.databinding.diffList
import io.github.vvb2060.magisk.databinding.set
import io.github.vvb2060.magisk.dialog.LocalModuleInstallDialog
import io.github.vvb2060.magisk.dialog.OnlineModuleInstallDialog
import io.github.vvb2060.magisk.events.GetContentEvent
import io.github.vvb2060.magisk.events.SnackbarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import io.github.vvb2060.magisk.core.R as CoreR

class ModuleViewModel : AsyncLoadViewModel() {

    val bottomBarBarrierIds = intArrayOf(R.id.module_update, R.id.module_remove)

    private val itemsInstalled = diffList<LocalModuleRvItem>()

    val items = MergeObservableList<RvItem>()
    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }

    val data get() = uri

    @get:Bindable
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    override suspend fun doLoadWork() {
        loading = true
        val moduleLoaded = Info.env.isActive &&
                withContext(Dispatchers.IO) { LocalModule.loaded() }
        if (moduleLoaded) {
            loadInstalled()
            if (items.isEmpty()) {
                items.insertItem(InstallModule)
                    .insertList(itemsInstalled)
            }
        }
        loading = false
        loadUpdateInfo()
    }

    override fun onNetworkChanged(network: Boolean) = startLoading()

    private suspend fun loadInstalled() {
        withContext(Dispatchers.Default) {
            val installed = LocalModule.installed().map { LocalModuleRvItem(it) }
            itemsInstalled.update(installed)
        }
    }

    private suspend fun loadUpdateInfo() {
        withContext(Dispatchers.IO) {
            itemsInstalled.forEach {
                if (it.item.fetch())
                    it.fetchedUpdateInfo()
            }
        }
    }

    fun downloadPressed(item: OnlineModule?) =
        if (item != null && Info.isConnected.value == true) {
            withExternalRW { OnlineModuleInstallDialog(item).show() }
        } else {
            SnackbarEvent(CoreR.string.no_connection).publish()
        }

    fun installPressed() = withExternalRW {
        GetContentEvent("application/zip", UriCallback()).publish()
    }

    fun requestInstallLocalModule(uri: Uri, displayName: String) {
        LocalModuleInstallDialog(this, uri, displayName).show()
    }

    @Parcelize
    class UriCallback : ContentResultCallback {
        override fun onActivityResult(result: Uri) {
            uri.value = result
        }
    }

    fun runAction(id: String, name: String) {
        MainDirections.actionActionFragment(id, name).navigate()
    }

    companion object {
        private val uri = MutableLiveData<Uri?>()
    }
}
