package com.lagradost.cloudstream3.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

abstract class BaseFragment<T : ViewBinding>(
    private val bindingCreator: BindingCreator<T>
) : Fragment() {

    private var _binding: T? = null
    protected val binding: T? get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutId = pickLayout()
        val root: View? = layoutId?.let { inflater.inflate(it, container, false) }
        _binding = try {
            when (bindingCreator) {
                is BindingCreator.Inflate -> bindingCreator.fn(inflater, container, false)
                is BindingCreator.Bind -> {
                    if (root != null) bindingCreator.fn(root)
                    else throw IllegalStateException("Root view is null for bind()")
                }
            }
        } catch (t: Throwable) {
            showToast(
                txt(R.string.unable_to_inflate, t.message ?: ""),
                Toast.LENGTH_LONG
            )
            logError(t)
            null
        }

        return _binding?.root ?: root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixPadding(view)
        binding?.let { onBindingCreated(it, savedInstanceState) }
    }

    /** Called when binding has been safely created and view is ready. */
    protected open fun onBindingCreated(binding: T, savedInstanceState: Bundle?) {
        onBindingCreated(binding)
    }

    /** Called when binding has been safely created and view is ready. No savedInstanceState. */
    protected open fun onBindingCreated(binding: T) {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        binding?.apply { fixPadding(root) }
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @LayoutRes
    protected open fun pickLayout(): Int? = null

    protected open fun fixPadding(view: View) {
        fixSystemBarsPadding(view)
    }

    sealed class BindingCreator<T : ViewBinding> {
        class Inflate<T : ViewBinding>(
            val fn: (LayoutInflater, ViewGroup?, Boolean) -> T
        ) : BindingCreator<T>()

        class Bind<T : ViewBinding>(
            val fn: (View) -> T
        ) : BindingCreator<T>()
    }
}
