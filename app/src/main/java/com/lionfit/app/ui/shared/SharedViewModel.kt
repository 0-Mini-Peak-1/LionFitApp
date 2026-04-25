package com.lionfit.app.ui.shared

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lionfit.app.data.model.RunSession

class SharedViewModel : ViewModel() {
    // This holds the pre-calculated run while the user navigate to the save screen
    val pendingRunSession = MutableLiveData<RunSession>()
    // Update profile picture across the app
    val updatedProfilePicUrl = androidx.lifecycle.MutableLiveData<String?>()
    // signal for profile updates
    val profileUpdatedSignal = MutableLiveData<Long>(0L)}