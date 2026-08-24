package com.lagradost.cloudstream3.ui.revamp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.RevampActivityDesignSystemBinding

class RevampDesignSystemActivity : AppCompatActivity() {

    private lateinit var binding: RevampActivityDesignSystemBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RevampActivityDesignSystemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, RevampDesignSystemFragment.newInstance())
                .commit()
        }
    }
}
