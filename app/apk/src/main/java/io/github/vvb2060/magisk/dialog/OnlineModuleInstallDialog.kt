package io.github.vvb2060.magisk.dialog

import android.content.Context
import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.core.di.ServiceLocator
import io.github.vvb2060.magisk.core.download.DownloadEngine
import io.github.vvb2060.magisk.core.download.Subject
import io.github.vvb2060.magisk.core.model.module.OnlineModule
import io.github.vvb2060.magisk.ui.flash.FlashFragment
import io.github.vvb2060.magisk.view.MagiskDialog
import io.github.vvb2060.magisk.view.Notifications
import kotlinx.parcelize.Parcelize

class OnlineModuleInstallDialog(private val item: OnlineModule) : MarkDownDialog() {

    private val svc get() = ServiceLocator.networkService

    override suspend fun getMarkdownText(): String {
        val str = svc.fetchString(item.changelog)
        return if (str.length > 1000) str.substring(0, 1000) else str
    }

    @Parcelize
    class Module(
        override val module: OnlineModule,
        override val autoLaunch: Boolean,
        override val notifyId: Int = Notifications.nextId()
    ) : Subject.Module() {
        override fun pendingIntent(context: Context) = FlashFragment.installIntent(context, file)
    }

    override fun build(dialog: MagiskDialog) {
        super.build(dialog)
        dialog.apply {

            fun download(install: Boolean) {
                DownloadEngine.startWithActivity(activity, Module(item, install))
            }

            val title = context.getString(R.string.repo_install_title,
                item.name, item.version, item.versionCode)

            setTitle(title)
            setCancelable(true)
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = R.string.download
                onClick { download(false) }
            }
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.install
                onClick { download(true) }
            }
            setButton(MagiskDialog.ButtonType.NEUTRAL) {
                text = android.R.string.cancel
            }
        }
    }

}
