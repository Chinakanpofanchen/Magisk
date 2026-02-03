package io.github.vvb2060.magisk.ui.superuser

import android.graphics.drawable.Drawable
import androidx.databinding.Bindable
import io.github.vvb2060.magisk.BR
import io.github.vvb2060.magisk.R
import io.github.vvb2060.magisk.core.AppContext
import io.github.vvb2060.magisk.core.Config
import io.github.vvb2060.magisk.core.model.su.SuPolicy
import io.github.vvb2060.magisk.databinding.DiffItem
import io.github.vvb2060.magisk.databinding.ItemWrapper
import io.github.vvb2060.magisk.databinding.ObservableRvItem
import io.github.vvb2060.magisk.databinding.set
import io.github.vvb2060.magisk.core.R as CoreR

class PolicyRvItem(
    private val viewModel: SuperuserViewModel,
    override val item: SuPolicy,
    val packageName: String,
    private val isSharedUid: Boolean,
    val icon: Drawable,
    val appName: String
) : ObservableRvItem(), DiffItem<PolicyRvItem>, ItemWrapper<SuPolicy> {

    override val layoutRes = R.layout.item_policy_md2

    val title get() = if (isSharedUid) "[SharedUID] $appName" else appName

    val statusText: String
        get() = when (item.policy) {
            SuPolicy.ALLOW -> AppContext.getString(CoreR.string.superuser_status_authorized)
            SuPolicy.DENY -> AppContext.getString(CoreR.string.superuser_status_denied)
            SuPolicy.RESTRICT -> AppContext.getString(CoreR.string.superuser_status_restricted)
            else -> AppContext.getString(CoreR.string.superuser_status_not_set)
        }

    private inline fun <reified T> setImpl(new: T, old: T, setter: (T) -> Unit) {
        if (old != new) {
            setter(new)
        }
    }

    @get:Bindable
    var isExpanded = false
        set(value) = set(value, field, { field = it }, BR.expanded)

    val showSlider = Config.suRestrict || item.policy == SuPolicy.RESTRICT

    @get:Bindable
    var isEnabled
        get() = item.policy >= SuPolicy.ALLOW
        set(value) = setImpl(value, isEnabled) {
            notifyPropertyChanged(BR.enabled)
            viewModel.updatePolicy(this, if (it) SuPolicy.ALLOW else SuPolicy.DENY)
        }

    @get:Bindable
    var sliderValue
        get() = item.policy
        set(value) = setImpl(value, sliderValue) {
            notifyPropertyChanged(BR.sliderValue)
            notifyPropertyChanged(BR.enabled)
            viewModel.updatePolicy(this, it)
        }

    val sliderValueToPolicyString: (Float) -> Int = { value ->
        when (value.toInt()) {
            1 -> CoreR.string.deny
            2 -> CoreR.string.restrict
            3 -> CoreR.string.grant
            else -> CoreR.string.deny
        }
    }

    @get:Bindable
    var shouldNotify
        get() = item.notification
        private set(value) = setImpl(value, shouldNotify) {
            item.notification = it
            viewModel.updateNotify(this)
        }

    @get:Bindable
    var shouldLog
        get() = item.logging
        private set(value) = setImpl(value, shouldLog) {
            item.logging = it
            viewModel.updateLogging(this)
        }

    fun toggleExpand() {
        isExpanded = !isExpanded
    }

    fun toggleNotify() {
        shouldNotify = !shouldNotify
    }

    fun toggleLog() {
        shouldLog = !shouldLog
    }

    fun revoke() {
        viewModel.deletePressed(this)
    }

    override fun itemSameAs(other: PolicyRvItem) = packageName == other.packageName

    override fun contentSameAs(other: PolicyRvItem) = item.policy == other.item.policy

}
