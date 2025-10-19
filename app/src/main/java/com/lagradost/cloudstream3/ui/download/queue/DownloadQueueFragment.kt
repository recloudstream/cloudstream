package com.lagradost.cloudstream3.ui.download.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.databinding.FragmentDownloadQueueBinding

class DownloadQueueFragment : Fragment() {
//    private lateinit var downloadsViewModel: DownloadViewModel
    private var binding: FragmentDownloadQueueBinding? = null

    companion object {
        fun newInstance(): Bundle {
            return Bundle().apply {

            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//        downloadsViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        val localBinding = FragmentDownloadQueueBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }
}