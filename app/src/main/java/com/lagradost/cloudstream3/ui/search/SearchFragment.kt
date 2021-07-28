package com.lagradost.cloudstream3.ui.search

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.APIHolder.allApi
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiSettings
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.UIHelper.loadResult
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import kotlinx.android.synthetic.main.fragment_search.*

class SearchFragment : Fragment() {

    private lateinit var searchViewModel: SearchViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        searchViewModel =
            ViewModelProvider(this).get(SearchViewModel::class.java)
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    private fun fixGrid() {
        val compactView = activity?.getGridIsCompact() ?: false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            cardSpace.spanCount = spanCountLandscape
        } else {
            cardSpace.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.fixPaddingStatusbar(searchRoot)
        fixGrid()

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            SearchAdapter(
                it,
                ArrayList(),
                cardSpace,
            )
        }

        cardSpace.adapter = adapter
        search_loading_bar.alpha = 0f

        val searchExitIcon = main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchMagIcon = main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f
        search_filter.setOnClickListener {
            val apiNamesSetting = activity?.getApiSettings()
            if (apiNamesSetting != null) {
                val apiNames = apis.map { it.name }
                val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

                builder.setMultiChoiceItems(apiNames.toTypedArray(),
                    apiNames.map { a -> apiNamesSetting.contains(a) }.toBooleanArray()
                ) { _, position: Int, checked: Boolean ->
                    val apiNamesSettingLocal = activity?.getApiSettings()
                    if (apiNamesSettingLocal != null) {
                        val settingsManagerLocal = PreferenceManager.getDefaultSharedPreferences(activity)
                        if (checked) {
                            apiNamesSettingLocal.add(apiNames[position])
                        } else {
                            apiNamesSettingLocal.remove(apiNames[position])
                        }

                        val edit = settingsManagerLocal.edit()
                        edit.putStringSet(getString(R.string.search_providers_list_key),
                            apiNames.filter { a -> apiNamesSettingLocal.contains(a) }.toSet())
                        edit.apply()
                        allApi.providersActive = apiNamesSettingLocal
                    }
                }
                builder.setTitle("Search Providers")
                builder.setNegativeButton("Ok") { _, _ -> }
                builder.show()
            }
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchViewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchViewModel.quickSearch(newText)
                return true
            }
        })

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        (cardSpace?.adapter as SearchAdapter?)?.cardList = data
                        cardSpace?.adapter?.notifyDataSetChanged()
                    }
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Failure -> {
                    Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon.alpha = 0f
                    search_loading_bar.alpha = 1f
                }
            }
        }
        allApi.providersActive = requireActivity().getApiSettings()

        main_search.setOnQueryTextFocusChangeListener { searchView, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                searchView?.postDelayed({
                    val imm: InputMethodManager? =
                        requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager?
                    imm?.showSoftInput(view.findFocus(), 0)
                }, 200)
            }
        }
        main_search.onActionViewExpanded()
        //searchViewModel.search("iron man")
        //(activity as AppCompatActivity).loadResult("https://shiro.is/overlord-dubbed", "overlord-dubbed", "Shiro")
/*
        (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_anim,
                R.anim.exit_anim,
                R.anim.pop_enter,
                R.anim.pop_exit)
            .add(R.id.homeRoot, PlayerFragment.newInstance(PlayerData(0, null,0)))
            .commit()*/
    }
}