package io.github.vvb2060.magisk.core.base

import android.app.job.JobService
import android.content.Context
import io.github.vvb2060.magisk.core.patch

abstract class BaseJobService : JobService() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.patch())
    }
}
