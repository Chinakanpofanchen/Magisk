package io.github.vvb2060.magisk.dialog

import android.app.ProgressDialog
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import io.github.vvb2060.magisk.arch.NavigationActivity
import io.github.vvb2060.magisk.arch.UIActivity
import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.core.ktx.toast
import io.github.vvb2060.magisk.core.tasks.MagiskInstaller
import io.github.vvb2060.magisk.events.DialogBuilder
import io.github.vvb2060.magisk.ui.flash.FlashFragment
import io.github.vvb2060.magisk.view.MagiskDialog
import kotlinx.coroutines.launch

class UninstallDialog : DialogBuilder {

    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(R.string.uninstall_magisk_title)
            setMessage(R.string.uninstall_magisk_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.restore_img
                onClick { restore(dialog.activity) }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = R.string.complete_uninstall
                onClick { completeUninstall(dialog) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun restore(activity: UIActivity<*>) {
        val dialog = ProgressDialog(activity).apply {
            setMessage(activity.getString(R.string.restore_img_msg))
            show()
        }

        activity.lifecycleScope.launch {
            MagiskInstaller.Restore().exec { success ->
                dialog.dismiss()
                if (success) {
                    activity.toast(R.string.restore_done, Toast.LENGTH_SHORT)
                } else {
                    activity.toast(R.string.restore_fail, Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun completeUninstall(dialog: MagiskDialog) {
        (dialog.ownerActivity as NavigationActivity<*>)
            .navigation.navigate(FlashFragment.uninstall())
    }

}
