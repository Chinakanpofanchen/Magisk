package com.kpfc.magisk.view

import com.kpfc.magisk.R
import com.kpfc.magisk.databinding.DiffItem
import com.kpfc.magisk.databinding.ItemWrapper
import com.kpfc.magisk.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
