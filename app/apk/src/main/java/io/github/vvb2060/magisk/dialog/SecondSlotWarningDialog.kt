package io.github.vvb2060.magisk.dialog

import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.events.DialogBuilder
import io.github.vvb2060.magisk.view.MagiskDialog

class SecondSlotWarningDialog : DialogBuilder {

    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(android.R.string.dialog_alert_title)
            setMessage(R.string.install_inactive_slot_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
            }
            setCancelable(true)
        }
    }
}
