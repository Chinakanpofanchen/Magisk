package io.github.vvb2060.magisk.dialog

import android.net.Uri
import io.github.vvb2060.magisk.MainDirections
import io.github.vvb2060.magisk.core.Const
import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.events.DialogBuilder
import io.github.vvb2060.magisk.ui.module.ModuleViewModel
import io.github.vvb2060.magisk.view.MagiskDialog

class LocalModuleInstallDialog(
    private val viewModel: ModuleViewModel,
    private val uri: Uri,
    private val displayName: String
) : DialogBuilder {
    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(R.string.confirm_install_title)
            setMessage(context.getString(R.string.confirm_install, displayName))
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
                onClick {
                    viewModel.apply {
                        MainDirections.actionFlashFragment(Const.Value.FLASH_ZIP, uri).navigate()
                    }
                }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = android.R.string.cancel
            }
        }
    }
}
