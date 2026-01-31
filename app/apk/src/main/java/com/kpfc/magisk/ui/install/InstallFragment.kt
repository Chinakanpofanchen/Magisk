package com.kpfc.magisk.ui.install

import com.kpfc.magisk.R
import com.kpfc.magisk.arch.BaseFragment
import com.kpfc.magisk.arch.viewModel
import com.kpfc.magisk.databinding.FragmentInstallMd2Binding
import com.kpfc.magisk.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
