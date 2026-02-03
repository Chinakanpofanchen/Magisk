package io.github.vvb2060.magisk.dialog

import io.github.vvb2060.magisk.core.AppContext
import io.github.vvb2060.magisk.core.Info
import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.core.download.DownloadEngine
import io.github.vvb2060.magisk.core.download.Subject
import io.github.vvb2060.magisk.view.MagiskDialog
import java.io.File

class ManagerInstallDialog : MarkDownDialog() {

    override suspend fun getMarkdownText(): String {
        val text = Info.update.note
        // Cache the changelog
        File(AppContext.cacheDir, "${Info.update.versionCode}.md").writeText(text)
        return text
    }

    override fun build(dialog: MagiskDialog) {
        super.build(dialog)
        dialog.apply {
            setCancelable(true)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.install
                onClick { DownloadEngine.startWithActivity(activity, Subject.App()) }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = android.R.string.cancel
            }
        }
    }

}
