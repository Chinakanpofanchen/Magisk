package io.github.vvb2060.magisk.ui.flash

import android.view.MenuItem
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.magisk.BR
import io.github.vvb2060.magisk.R
import io.github.vvb2060.magisk.arch.BaseViewModel
import io.github.vvb2060.magisk.core.Const
import io.github.vvb2060.magisk.core.Info
import io.github.vvb2060.magisk.core.ktx.reboot
import io.github.vvb2060.magisk.core.ktx.synchronized
import io.github.vvb2060.magisk.core.ktx.timeFormatStandard
import io.github.vvb2060.magisk.core.ktx.toTime
import io.github.vvb2060.magisk.core.tasks.FlashZip
import io.github.vvb2060.magisk.core.tasks.MagiskInstaller
import io.github.vvb2060.magisk.core.utils.MediaStoreUtils
import io.github.vvb2060.magisk.core.utils.MediaStoreUtils.outputStream
import io.github.vvb2060.magisk.databinding.set
import io.github.vvb2060.magisk.events.SnackbarEvent
import com.topjohnwu.superuser.CallbackList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FlashViewModel : BaseViewModel() {

    enum class State {
        FLASHING, SUCCESS, FAILED
    }

    private val _state = MutableLiveData(State.FLASHING)
    val state: LiveData<State> get() = _state
    val flashing = state.map { it == State.FLASHING }

    @get:Bindable
    var showReboot = Info.isRooted
        set(value) = set(value, field, { field = it }, BR.showReboot)

    val items = ObservableArrayList<ConsoleItem>()
    lateinit var args: FlashFragmentArgs

    private val logItems = mutableListOf<String>().synchronized()
    private val outItems = object : CallbackList<String>() {
        override fun onAddElement(e: String?) {
            e ?: return
            items.add(ConsoleItem(e))
            logItems.add(e)
        }
    }

    fun startFlashing() {
        val (action, uri) = args

        viewModelScope.launch {
            val result = when (action) {
                Const.Value.FLASH_ZIP -> {
                    uri ?: return@launch
                    FlashZip(uri, outItems, logItems).exec()
                }
                Const.Value.UNINSTALL -> {
                    showReboot = false
                    MagiskInstaller.Uninstall(outItems, logItems).exec()
                }
                Const.Value.FLASH_MAGISK -> {
                    if (Info.isEmulator)
                        MagiskInstaller.Emulator(outItems, logItems).exec()
                    else
                        MagiskInstaller.Direct(outItems, logItems).exec()
                }
                Const.Value.FLASH_INACTIVE_SLOT -> {
                    showReboot = false
                    MagiskInstaller.SecondSlot(outItems, logItems).exec()
                }
                Const.Value.PATCH_FILE -> {
                    uri ?: return@launch
                    showReboot = false
                    MagiskInstaller.Patch(uri, outItems, logItems).exec()
                }
                else -> {
                    back()
                    return@launch
                }
            }
            onResult(result)
        }
    }

    private fun onResult(success: Boolean) {
        _state.value = if (success) State.SUCCESS else State.FAILED
    }

    fun onMenuItemClicked(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> savePressed()
        }
        return true
    }

    private fun savePressed() = withExternalRW {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "magisk_install_log_%s.log".format(
                System.currentTimeMillis().toTime(timeFormatStandard)
            )
            val file = MediaStoreUtils.getFile(name)
            file.uri.outputStream().bufferedWriter().use { writer ->
                synchronized(logItems) {
                    logItems.forEach {
                        writer.write(it)
                        writer.newLine()
                    }
                }
            }
            SnackbarEvent(file.toString()).publish()
        }
    }

    fun restartPressed() = reboot()
}
