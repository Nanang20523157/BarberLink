package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.BundlingChangeInfo

open class ConfirmDeleteViewModel : ViewModel() {

    protected val _bundleChangeList = MutableLiveData<List<BundlingChangeInfo>>(emptyList())
    val bundleChangeList: LiveData<List<BundlingChangeInfo>> = _bundleChangeList

    fun setBundleChangeList(list: List<BundlingChangeInfo>) {
        _bundleChangeList.value = list
    }

}