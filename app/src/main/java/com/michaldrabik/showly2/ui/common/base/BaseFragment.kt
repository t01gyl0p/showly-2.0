package com.michaldrabik.showly2.ui.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.michaldrabik.showly2.di.DaggerViewModelFactory
import com.michaldrabik.showly2.ui.common.UiModel
import com.michaldrabik.showly2.ui.main.MainActivity
import com.michaldrabik.showly2.utilities.extensions.showErrorSnackbar
import com.michaldrabik.showly2.utilities.extensions.showInfoSnackbar
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject

abstract class BaseFragment<T : BaseViewModel<out UiModel>> : Fragment() {

  @Inject lateinit var viewModelFactory: DaggerViewModelFactory

  protected abstract val layoutResId: Int
  protected abstract val viewModel: T

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(layoutResId, container, false)

  protected fun hideNavigation(animate: Boolean = true) =
    getMainActivity().hideNavigation(animate)

  protected fun showNavigation(animate: Boolean = true) =
    getMainActivity().showNavigation(animate)

  protected fun showInfoSnackbar(@StringRes messageResId: Int) =
    getSnackbarHost().showInfoSnackbar(getString(messageResId))

  protected fun showErrorSnackbar(@StringRes errorResId: Int) =
    getSnackbarHost().showErrorSnackbar(getString(errorResId))

  protected open fun getSnackbarHost(): ViewGroup = getMainActivity().snackBarHost

  protected fun getMainActivity() = requireActivity() as MainActivity
}
