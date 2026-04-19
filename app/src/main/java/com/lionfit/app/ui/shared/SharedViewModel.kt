package com.lionfit.app.ui.shared

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lionfit.app.data.model.RunSession

class SharedViewModel : ViewModel() {
    // This holds the pre-calculated run while the user navigate to the save screen
    val pendingRunSession = MutableLiveData<RunSession>()
    val updatedProfilePicUrl = androidx.lifecycle.MutableLiveData<String?>()
}