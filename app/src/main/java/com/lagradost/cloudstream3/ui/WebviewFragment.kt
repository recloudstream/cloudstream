package com.lagradost.cloudstream3.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.databinding.FragmentWebviewBinding
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.loadRepository


class WebviewFragment : Fragment() {

    var binding: FragmentWebviewBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val url = arguments?.getString(WEBVIEW_URL) ?: "".also {
            findNavController().popBackStack()
        }

        binding?.webView?.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val requestUrl = request?.url.toString()
                val performedAction = MainActivity.handleAppIntentUrl(activity, requestUrl, true)
                if (performedAction) {
                    findNavController().popBackStack()
                    return true
                }

                return super.shouldOverrideUrlLoading(view, request)
            }
        }
        binding?.webView?.apply {
            WebViewResolver.webViewUserAgent = settings.userAgentString

            addJavascriptInterface(RepoApi(activity), "RepoApi")
            settings.javaScriptEnabled = true
            settings.userAgentString = USER_AGENT
            settings.domStorageEnabled = true
//        WebView.setWebContentsDebuggingEnabled(true)

            loadUrl(url)
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localBinding = FragmentWebviewBinding.inflate(inflater, container, false)
        binding = localBinding
        // Inflate the layout for this fragment
        return localBinding.root//inflater.inflate(R.layout.fragment_webview, container, false)
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