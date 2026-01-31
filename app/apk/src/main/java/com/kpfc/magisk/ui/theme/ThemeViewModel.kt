package com.kpfc.magisk.ui.theme

import com.kpfc.magisk.arch.BaseViewModel
import com.kpfc.magisk.core.Config
import com.kpfc.magisk.dialog.DarkThemeDialog
import com.kpfc.magisk.events.RecreateEvent
import com.kpfc.magisk.view.TappableHeadlineItem

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
