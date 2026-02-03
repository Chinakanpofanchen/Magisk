package io.github.vvb2060.magisk.dialog

import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.events.DialogBuilder
import io.github.vvb2060.magisk.view.MagiskDialog

class SuperuserRevokeDialog(
    private val appName: String,
    private val onSuccess: () -> Unit
) : DialogBuilder {

    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(R.string.su_revoke_title)
            setMessage(R.string.su_revoke_msg, appName)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
                onClick { onSuccess() }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = android.R.string.cancel
            }
        }
    }
}
