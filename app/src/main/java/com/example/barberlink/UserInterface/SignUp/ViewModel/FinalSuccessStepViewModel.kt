package com.example.barberlink.UserInterface.SignUp.ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FinalSuccessStepViewModel : ViewModel() {
    private var userAdminData: UserAdminData? = null
    private var userEmployeeData: UserEmployeeData? = null

    private var loginType: String = ""

    fun setUserAdminData(data: UserAdminData?) {
        viewModelScope.launch {
            userAdminData = data
        }
    }

    fun getUserAdminData(): UserAdminData? {
        return runBlocking {
            userAdminData
        }
    }

    fun setUserEmployeeData(data: UserEmployeeData?) {
        viewModelScope.launch {
            userEmployeeData = data
        }
    }

    fun getUserEmployeeData(): UserEmployeeData? {
        return runBlocking {
            userEmployeeData
        }
    }

    fun setLoginType(type: String) {
        viewModelScope.launch {
            loginType = type
        }
    }

    fun getLoginType(): String {
        return runBlocking {
            loginType
        }
    }

}