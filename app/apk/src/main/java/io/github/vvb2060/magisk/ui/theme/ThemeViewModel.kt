package io.github.vvb2060.magisk.ui.theme

import io.github.vvb2060.magisk.arch.BaseViewModel
import io.github.vvb2060.magisk.core.Config
import io.github.vvb2060.magisk.dialog.DarkThemeDialog
import io.github.vvb2060.magisk.events.RecreateEvent
import io.github.vvb2060.magisk.view.TappableHeadlineItem

class ThemeViewModel : BaseViewModel(), TappableHeadlineItem.Listener {

    val themeHeadline = TappableHeadlineItem.ThemeMode

    override fun onItemPressed(item: TappableHeadlineItem) = when (item) {
        is TappableHeadlineItem.ThemeMode -> DarkThemeDialog().show()
    }

    fun saveTheme(theme: Theme) {
        if (!theme.isSelected) {
            Config.themeOrdinal = theme.ordinal
            RecreateEvent().publish()
        }
    }
}
