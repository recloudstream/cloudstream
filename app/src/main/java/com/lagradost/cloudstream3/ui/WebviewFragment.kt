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
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.loadRepository
import kotlinx.android.synthetic.main.fragment_webview.*

class WebviewFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val url = arguments?.getString(WEBVIEW_URL) ?: "".also {
            findNavController().popBackStack()
        }

        web_view.webViewClient = object : WebViewClient() {
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

        WebViewResolver.webViewUserAgent = web_view.settings.userAgentString

        web_view.addJavascriptInterface(RepoApi(activity), "RepoApi")
        web_view.settings.javaScriptEnabled = true
        web_view.settings.userAgentString = USER_AGENT
        web_view.settings.domStorageEnabled = true
//        WebView.setWebContentsDebuggingEnabled(true)

        web_view.loadUrl(url)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_webview, container, false)
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