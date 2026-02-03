package io.github.vvb2060.magisk.view

import io.github.vvb2060.magisk.R
import io.github.vvb2060.magisk.databinding.DiffItem
import io.github.vvb2060.magisk.databinding.RvItem
import io.github.vvb2060.magisk.core.R as CoreR

sealed class TappableHeadlineItem : RvItem(), DiffItem<TappableHeadlineItem> {

    abstract val title: Int
    abstract val icon: Int

    override val layoutRes = R.layout.item_tappable_headline

    // --- listener

    interface Listener {

        fun onItemPressed(item: TappableHeadlineItem)

    }

    // --- objects

    object ThemeMode : TappableHeadlineItem() {
        override val title = CoreR.string.settings_dark_mode_title
        override val icon = R.drawable.ic_day_night
    }

}
