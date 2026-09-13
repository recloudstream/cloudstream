package com.lagradost.cloudstream3.services

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.lagradost.cloudstream3.ui.car.CarSession

class CSCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return CarSession()
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    override fun getCarAppThemeSource(): Int {
        return CarAppService.THEME_SOURCE_APP
    }
}
