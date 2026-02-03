package io.github.vvb2060.magisk.ui.install

import io.github.vvb2060.magisk.R
import io.github.vvb2060.magisk.arch.BaseFragment
import io.github.vvb2060.magisk.arch.viewModel
import io.github.vvb2060.magisk.databinding.FragmentInstallMd2Binding
import io.github.vvb2060.magisk.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
