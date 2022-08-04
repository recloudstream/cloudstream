package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse

class ResultFragmentTv : ResultFragment() {
    override val resultLayout = R.layout.fragment_result_tv
    override fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {

    }
}