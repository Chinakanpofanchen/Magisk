package io.github.vvb2060.magisk.view

import io.github.vvb2060.magisk.R
import io.github.vvb2060.magisk.databinding.DiffItem
import io.github.vvb2060.magisk.databinding.ItemWrapper
import io.github.vvb2060.magisk.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
