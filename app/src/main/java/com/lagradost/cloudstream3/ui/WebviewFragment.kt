package com.lagradost.cloudstream3.ui

import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.databinding.FragmentWebviewBinding
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppContextUtils.loadRepository

class WebviewFragment : BaseFragment<FragmentWebviewBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentWebviewBinding::inflate)
) {

    override fun fixLayout(view: View) = Unit

    override fun onBindingCreated(binding: FragmentWebviewBinding) {
        val url = arguments?.getString(WEBVIEW_URL) ?: "".also {
            findNavController().popBackStack()
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val requestUrl = request?.url?.toString() ?: return false
                val performedAction = MainActivity.handleAppIntentUrl(activity, requestUrl, true)
                if (performedAction) {
                    findNavController().popBackStack()
                    return true
                }

                if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://") &&
                    !requestUrl.startsWith("about:") && !requestUrl.startsWith("javascript:")) {
                    return try {
                        val intent = android.content.Intent.parseUri(requestUrl, android.content.Intent.URI_INTENT_SCHEME)
                        if (activity != null && intent.resolveActivity(activity!!.packageManager) != null) {
                            activity?.startActivity(intent)
                        }
                        true
                    } catch (e: Throwable) {
                        com.lagradost.cloudstream3.mvvm.logError(e)
                        true
                    }
                }

                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        binding.webView.apply {
            WebViewResolver.webViewUserAgent = settings.userAgentString

            addJavascriptInterface(RepoApi(activity), "RepoApi")
            settings.javaScriptEnabled = true
            settings.userAgentString = USER_AGENT
            settings.domStorageEnabled = true

            loadUrl(url)
        }
    }

    companion object {
        private const val WEBVIEW_URL = "webview_url"
        fun newInstance(webViewUrl: String) =
            Bundle().apply {
                putString(WEBVIEW_URL, webViewUrl)
            }
    }

    private class RepoApi(val activity: FragmentActivity?) {
        @JavascriptInterface
        fun installRepo(repoUrl: String) {
            activity?.loadRepository(repoUrl)
        }
    }
}
