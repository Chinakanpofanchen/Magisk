package io.github.vvb2060.magisk.core

import android.os.Bundle
import io.github.vvb2060.magisk.core.base.BaseProvider
import io.github.vvb2060.magisk.core.su.SuCallbackHandler

class Provider : BaseProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            SuCallbackHandler.LOG, SuCallbackHandler.NOTIFY -> {
                SuCallbackHandler.run(context!!, method, extras)
                Bundle.EMPTY
            }
            else -> Bundle.EMPTY
        }
    }
}
