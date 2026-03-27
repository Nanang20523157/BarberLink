package com.example.barberlink.UserInterface.SignUp.ViewModel

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.Utils.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitSafe
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AdminDataStepViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : ViewModel() {

    private var imageUri: Uri? = null
    private var imageCopy: Uri? = null
    private lateinit var userAdminData: UserAdminData
    private lateinit var userRolesData: UserRolesData
    private var userAdminCopy: UserAdminData = UserAdminData()
    private var existingAdminData: UserAdminData? = null
    private var useBarberNameNotVerified: Boolean = false
    private var useBarberEmailAnExisting: Boolean = false

    private val _registerResult = MutableLiveData<ResultState?>()
    val registerResult: LiveData<ResultState?> = _registerResult

    sealed class ResultState {
        data object Loading: ResultState()
        data class Navigate(val isAddData: Boolean, val uid: String): ResultState()
        data class Failure(val message: String, val step: String): ResultState()
        data class ShowToast(val message: String, val hideLoading: Boolean): ResultState()
    }

    fun setRegisterResult(value: ResultState?) {
        viewModelScope.launch {
            _registerResult.value = value
        }
    }

    fun setImageUri(uri: Uri?) {
        viewModelScope.launch {
            imageUri = uri
        }
    }

    fun setImageCopy(uri: Uri?) {
        viewModelScope.launch {
            imageCopy = uri
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            userAdminData = data
        }
    }

    fun setUserRolesData(data: UserRolesData) {
        viewModelScope.launch {
            userRolesData = data
        }
    }

    fun setUserAdminCopy(data: UserAdminData) {
        viewModelScope.launch {
            userAdminCopy = data
        }
    }

    fun getImageUri(): Uri? {
        return runBlocking {
            imageUri
        }
    }

    fun getImageCopy(): Uri? {
        return runBlocking {
            imageCopy
        }
    }

    fun getUserAdminData(): UserAdminData {
        return runBlocking {
            userAdminData
        }
    }

    fun getUserRolesData(): UserRolesData {
        return runBlocking {
            userRolesData
        }
    }

    fun getUserAdminCopy(): UserAdminData {
        return runBlocking {
            userAdminCopy
        }
    }

    fun getUserExistingData(): UserAdminData? {
        return runBlocking {
            existingAdminData
        }
    }

    fun getUserShouldReAuthenticate(): Boolean {
        return runBlocking {
            useBarberNameNotVerified && useBarberEmailAnExisting
        }
    }

    fun checkBarbershopName(name: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            // CHECK APAKAH NAMA BARBERSHOP SUDAH TERDAFTAR
            if (!NetworkMonitor.isOnline.value) {
                val errMessage = NetworkMonitor.errorMessage.value
                NetworkMonitor.showToast(errMessage, true)

                _registerResult.postValue(ResultState.Failure("", ""))
//            _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "RETRIEVE_UID"))
                return@launch
            }

            try {
                val formattedName = name.replace("\\s".toRegex(), "").lowercase().trim()
                Logger.d("CheckBarbershopName",
                    "formattedName: $formattedName"
                )
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("barbershops")
                        .whereEqualTo("barbershop_identifier", formattedName)
                        .awaitGetWithOfflineFallback(tag = "CheckBarbershopName")
                }

                var message = ""
                if (!snapshot.isSuccessful) message = if (snapshot.displayMessage) snapshot.errorMessage.toString() else "Gagal memeriksa nama barbershop!"
                _registerResult.value = ResultState.ShowToast(message, true)
                Logger.d("CheckBarbershopName",
                    "barbershopName: ${snapshot.data?.documents?.firstOrNull()?.toObject(UserAdminData::class.java)?.barbershopName ?: "null"}"
                )
                Logger.d("CheckBarbershopName",
                    "size: ${snapshot.data?.documents?.size ?: "null"}"
                )
                if (snapshot.isSuccessful) {
                    existingAdminData = snapshot.data?.documents?.firstOrNull()?.toObject(UserAdminData::class.java)
                    useBarberNameNotVerified = existingAdminData?.accountVerification == false
                    if (useBarberNameNotVerified) {
                        callback(false)
                        return@launch
                    }
                    val document = snapshot.data?.documents as? List<*> ?: emptyList<String>()
                    callback(document.isNotEmpty())
                }
            } catch (e: Exception) {
                _registerResult.value = ResultState.ShowToast("Gagal memeriksa nama barbershop!", true)
            }
        }
    }

    fun checkUsernameAdmin(targetUsername: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            // PENGECHECKAN MANUAL KARENA GAK OFFLINEAWARE
            if (!NetworkMonitor.isOnline.value) {
                val errMessage = NetworkMonitor.errorMessage.value
                NetworkMonitor.showToast(errMessage, true)

                _registerResult.postValue(ResultState.Failure("", ""))
//            _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "RETRIEVE_UID"))
                return@launch
            }

            try {
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("official")
                        .document("barberlink2024")
                        .awaitGetWithOfflineFallback(tag = "getBarberlinkOfficialData")
                }

                var message = ""
                if (!snapshot.isSuccessful) message = if (snapshot.displayMessage) snapshot.errorMessage.toString() else "Gagal memeriksa username admin!"
                _registerResult.value = ResultState.ShowToast(message, true)
                
                if (snapshot.isSuccessful) {
                    val document = snapshot.data
                    if (document != null && document.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val listUsernames = document.get("username_list") as? List<String> ?: emptyList()
                        // Melakukan pengecekan dengan menormalisasi isi list ke lowercase
                        val isUsernameTaken = listUsernames.any { it.equals(targetUsername, ignoreCase = true) }
                        callback(isUsernameTaken)
                    } else {
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                _registerResult.value = ResultState.ShowToast("Gagal memeriksa username admin!", true)
            }
        }
    }
    
    fun checkEmailExists(email: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            // PENGECHECKAN MANUAL KARENA GAK OFFLINEAWARE
            if (!NetworkMonitor.isOnline.value) {
                val errMessage = NetworkMonitor.errorMessage.value
                NetworkMonitor.showToast(errMessage, true)

                _registerResult.postValue(ResultState.Failure("", ""))
//            _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "RETRIEVE_UID"))
                return@launch
            }

            try {
                auth.fetchSignInMethodsForEmail(email)
                    .addOnCompleteListener { task ->
                        var message = ""
                        if (!task.isSuccessful) message = "Gagal memeriksa email barbershop!"

                        _registerResult.value = ResultState.ShowToast(message, true)
                        if (task.isSuccessful) {
                            val signInMethods = task.result?.signInMethods ?: emptyList<String>()
                            val emailIsExist = signInMethods.isNotEmpty()
                            if (useBarberNameNotVerified && emailIsExist && email == existingAdminData?.email) {
                                useBarberEmailAnExisting = true
                                callback(false)
                                return@addOnCompleteListener
                            }
                            callback(signInMethods.isNotEmpty())
                        }
                    }
            } catch (e: Exception) {
                _registerResult.value = ResultState.ShowToast("Gagal memeriksa email barbershop!", true)
            }
        }
    }

//    private fun isConnectedToInternet(): Boolean {
//        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val network = connectivityManager.activeNetwork ?: return false
//        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
//        return when {
//            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
//            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
//            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
//            else -> false
//        }
//    }

    // Kelas untuk cek internet dengan ping
//    private class InternetCheck(private val onInternetChecked: (Boolean) -> Unit) : AsyncTask<Void, Void, Boolean>() {
//        override fun doInBackground(vararg params: Void?): Boolean {
//            return try {
//                Log.d("InternetCheck", "Checking internet connection...1")
//                val ipAddr = java.net.InetAddress.getByName("8.8.8.8") // Ping ke Google DNS
//                ipAddr.isReachable(3000) // Timeout 3 detik
//            } catch (e: IOException) {
//                Log.d("InternetCheck", "Checking internet connection...2")
//                false
//            }
//        }
//
//        override fun onPostExecute(result: Boolean) {
//            Log.d("InternetCheck", "Internet check result: $result")
//            onInternetChecked(result)
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun addNewUserAdminToDatabase(sameAccount: Boolean) {
        viewModelScope.launch {
            if (sameAccount) {
                _registerResult.postValue(ResultState.Loading)
                // PENGECHECKAN MANUAL KARENA GAK OFFLINEAWARE
                if (!NetworkMonitor.isOnline.value) {
                    val errMessage = NetworkMonitor.errorMessage.value
                    NetworkMonitor.showToast(errMessage, true)

                    _registerResult.postValue(ResultState.Failure("", "UPLOAD_IMAGE"))
//            _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "UPLOAD_IMAGE"))
                    return@launch
                }

                _registerResult.value = ResultState.ShowToast("Membuat akun barbershop!", false)

                imageUri?.let {
//                Toast.makeText(this, "Uplouding Image...", Toast.LENGTH_SHORT).show()
                    // Upload image to Firebase Storage
                    try {
                        val storageRef = storage.reference.child("profiles/${userAdminData.uid}")
                        storageRef.putFile(it)
                            .addOnSuccessListener {
                                storageRef.downloadUrl.addOnSuccessListener { url ->
                                    imageCopy = imageUri
                                    userAdminData.imageCompanyProfile = url.toString()
                                    saveNewDataAdminToFirestore()
                                }.addOnFailureListener { exception ->
                                    _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat mengunggah gambar barbershop!", "UPLOAD_IMAGE"))
                                }
                            }.addOnFailureListener { exception ->
                                _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat mengunggah gambar barbershop!", "UPLOAD_IMAGE"))
                            }
                    } catch (e: Exception) {
                        _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat mengunggah gambar barbershop!", "UPLOAD_IMAGE"))
                    }
                } ?: saveNewDataAdminToFirestore()
            } else {
                Log.d("UAD", "345")
                _registerResult.postValue(ResultState.Navigate(false, ""))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun saveNewDataAdminToFirestore() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            try {
                val task = withContext(Dispatchers.IO) {
                    db.collection("barbershops")
                        .document(userAdminData.uid)
                        .set(userAdminData)
                        .awaitWriteWithOfflineFallback(tag = "SaveNewAdminData")
                }

                if (task.isSuccessful) {
                    // not end process
                    userAdminData.userRef = "barbershops/${userAdminData.uid}"
                    userAdminCopy = userAdminData.copy()
                    clearOutletsAndAddNew()
                } else {
                    if (task.displayMessage) _registerResult.postValue(ResultState.Failure(task.errorMessage.toString(), "SAVE_DATA"))
                    else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat menyimpan data admin barbershop!", "SAVE_DATA"))
                }
            } catch (e:  Exception) {
                _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat menyimpan data admin barbershop!", "SAVE_DATA"))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun clearOutletsAndAddNew() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("barbershops")
                        .document(userAdminData.uid)
                        .collection("outlets")
                        .awaitGetWithOfflineFallback(tag = "clearOutletsAndAddNew")
                }

                if (snapshot.isSuccessful) {
                    val documents = snapshot.data
                    if (documents != null) {
                        if (documents.isEmpty) {
                            runAddOutletAndService()
                        } else {
                            val batch = db.batch()
                            documents.forEach { batch.delete(it.reference) }

                            val task = withContext(Dispatchers.IO) {
                                batch.commit().awaitWriteWithOfflineFallback(tag = "ClearOutletsBatch")
                            }
                            if (task.isSuccessful) {
                                // not end process
                                runAddOutletAndService()
                            } else {
                                if (task.displayMessage) _registerResult.postValue(ResultState.Failure(task.errorMessage.toString(), "BATCH_DELETE"))
                                else _registerResult.postValue(ResultState.Failure("Gagal menginisiasi data outlet barbershop!", "BATCH_DELETE"))
                            }
                        }
                    } else {
                        if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString(), "BATCH_DELETE"))
                        else _registerResult.postValue(ResultState.Failure("Gagal menginisiasi data outlet barbershop!", "BATCH_DELETE"))
                    }
                } else {
                    if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString(), "BATCH_DELETE"))
                    else _registerResult.postValue(ResultState.Failure("Gagal menginisiasi data outlet barbershop!", "BATCH_DELETE"))
                }
            } catch (e: Exception) {
                _registerResult.postValue(ResultState.Failure("Gagal menginisiasi data outlet barbershop!", "BATCH_DELETE"))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun runAddOutletAndService() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            try {
                val taskFailed = AtomicBoolean(false)
                val addOutletJob = async {
                    val prosesStatus = addOutletDataBarbershopAsync()
                    if (prosesStatus) { taskFailed.set(true) }
                }
                val addServiceJob = async {
                    val prosesStatus = addDefaultItemServiceAsync()
                    if (prosesStatus) { taskFailed.set(true) }
                }
                // Wait for both tasks to complete
                addOutletJob.await()
                addServiceJob.await()

                if (taskFailed.get()) {
                    _registerResult.postValue(ResultState.Failure("Gagal menambahkan data outlet barbershop!", "ADD_SUPPORT_DATA"))
                } else {
                    // Run updateUserRolesAndProfile if both are successful
                    updateUserRolesAndProfile()
                }
            } catch (e: Exception) {
                _registerResult.postValue(ResultState.Failure("Gagal menambahkan data outlet barbershop!", "ADD_SUPPORT_DATA"))
            }
        }
    }

    private suspend fun addOutletDataBarbershopAsync(): Boolean {
        val uidOutlet = userAdminData.barbershopIdentifier + "01"
        val outletData = Outlet(
            uid = uidOutlet,
            outletName = userAdminData.barbershopName + " 01",
            outletPhoneNumber = userAdminData.phone,
            rootRef = "barbershops/${userAdminData.uid}",
            listServices = mutableListOf("BSoBVRz4H5wkAeppmTJw")
        )

        val task = withContext(Dispatchers.IO) {
            db.collection("barbershops")
                .document(userAdminData.uid)
                .collection("outlets")
                .document(uidOutlet)
                .set(outletData)
                .awaitWriteWithOfflineFallback(tag = "AddOutletData")
        }

        return !task.isSuccessful // true = gagal
    }

    private suspend fun addDefaultItemServiceAsync(): Boolean {
        val defaultService = Service(
            applyToGeneral = true,
            autoSelected = true,
            //categoryDetail = "VOeNhb893iaDpKdaICOD",
            defaultItem = true,
            freeOfCharge = true,
            resultsShareAmount = mapOf("all" to 0),
            rootRef = "barbershops/${userAdminData.uid}",
            serviceCategory = "Conversation",
            serviceCounting = 0,
            serviceDesc = "Hair Care and Consultation ...",
            serviceIcon = "https://firebasestorage.googleapis.com/v0/b/barberlink-bfb66.appspot.com/o/services%2Ficons%2FBSoBVRz4H5wkAeppmTJw.png?alt=media&token=3c3f9c48-5368-4507-bc65-1da71b0d1ab3",
            serviceImg = "https://firebasestorage.googleapis.com/v0/b/barberlink-bfb66.appspot.com/o/services%2Fimages%2FBSoBVRz4H5wkAeppmTJw.png?alt=media&token=958c06d4-14f5-42f8-a912-410ede1aa6e7",
            serviceName = "Hair Care and Consultation",
            servicePrice = 0,
            serviceRating = 4.5,
            uid = "BSoBVRz4H5wkAeppmTJw"
        )

        val task = withContext(Dispatchers.IO) {
            db.collection("barbershops")
                .document(userAdminData.uid)
                .collection("services")
                .document(defaultService.uid)
                .set(defaultService)
                .awaitWriteWithOfflineFallback(tag = "AddDefaultService")
        }

        return !task.isSuccessful
    }

    private suspend fun updateCustomerPhotoProfile(): Boolean {
        val updates = hashMapOf<String, Any?>(
            "photo_profile" to userAdminData.imageCompanyProfile
        )

        val task = withContext(Dispatchers.IO) {
            db.document(userRolesData.customerRef)
                .update(updates)
                .awaitWriteWithOfflineFallback(tag = "UpdateCustomerPhotoProfile")
        }

        return !task.isSuccessful
    }

    private suspend fun updateEmployeePhotoProfile(): Boolean {
        val updates = hashMapOf<String, Any?>(
            "photo_profile" to userAdminData.imageCompanyProfile
        )

        val task = withContext(Dispatchers.IO) {
            db.document(userRolesData.employeeRef)
                .update(updates)
                .awaitWriteWithOfflineFallback(tag = "UpdateEmployeePhotoProfile")
        }

        return !task.isSuccessful
    }

//    private suspend fun updateUserRoles(): Boolean {
//        val userRolesCopy = userRolesData.copy().apply {
//            role = when (role) {
//                "" -> "admin"
//                "employee" -> "pairAE"
//                "pairEC(-)" -> "hybrid(-)"
//                "pairEC(+)" -> "hybrid(+)"
//                "customer" -> "pairAC(+)"
//                else -> "pairAC(-)"
//            }
//        }
//
//        val task = withContext(Dispatchers.IO) {
//            db.collection("users")
//                .document(userAdminData.phone)
//                .set(userRolesCopy)
//                .awaitWriteWithOfflineFallback(tag = "UpdateUserRoles")
//        }
//
//        return !task.isSuccessful
//    }

//    @RequiresApi(Build.VERSION_CODES.S)
//    fun updateUserRolesAndProfile() {
//        viewModelScope.launch {
//            _registerResult.postValue(ResultState.Loading)
//            try {
//                userRolesData.apply {
//                    adminProvider = "email"
//                    adminRef = "barbershops/${userAdminData.uid}"
//                    customerProvider = if (this.role == "undefined") "none" else this.customerProvider
//                    customerRef = if (this.role == "undefined") "customers/${userAdminData.phone}" else this.customerRef
//                    uid = userAdminData.phone
//                }
//
//                val updateJobs = mutableListOf<Deferred<Boolean>>()
//
//                updateJobs.add(async { updateUserRoles() })
////                when (userRolesData.role) {
////                    "" -> {}
////                    "employee" -> updateJobs.add(async { updateEmployeePhotoProfile() })
////                    "pairEC(-)", "pairEC(+)" -> {
////                        updateJobs.add(async { updateEmployeePhotoProfile() })
////                        updateJobs.add(async { updateCustomerPhotoProfile() })
////                    }
////                    "customer" -> {
////                        updateJobs.add(async { updateCustomerPhotoProfile() })
////                    } else -> updateJobs.add(async { updateCustomerPhotoProfile() })
////                }
//
//                val results = updateJobs.awaitAll()
//                // JIKA INGIN PARTIAL SCOPE DENGAN CHILD THROW EXCEPTIPN MAKA PAKAI SUPER_VISOR_SCOPE + RUN_CATCHING
//                // KODE AWAIT_ALL DIBAWAH INI TIDAK MENGIMPLEMENTASIKAN THROW APAPAUN PADA CHILDNYA (DI KODE INI IA RETURN FALSE KETIKA GAGAL) MAKA TIDAK PERLU SUPER_VISOR_SCOPE
//                // DITAMBAH SEBELUM MENGAKSES SERVER DENGAN GET, UPDATE, SET, ATAUPUN DELETE SUDAH DILAKUKAN PENGCHECKAN PATH SEPERTI NILAI ROOTREF YANG TIDAK BOLEH KOSONG
//                val allSuccess = results.all { !it }
//
//                if (allSuccess) {
//                    // PENGECHECKAN MANUAL KARENA GAK OFFLINEAWARE
//                    if (!NetworkMonitor.isOnline.value) {
//                        val errMessage = NetworkMonitor.errorMessage.value
//                        NetworkMonitor.showToast(errMessage, true)
//                        _registerResult.postValue(ResultState.Failure("", "UPDATE_ROLES"))
////                        _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "UPDATE_ROLES"))
//                        return@launch
//                    }
//
//                    auth.signInWithEmailAndPassword(
//                        userAdminData.email,
//                        userAdminData.password
//                    ).addOnCompleteListener { task ->
//                        if (task.isSuccessful) {
//                            val user = auth.currentUser
//                            user?.let {
//                                viewModelScope.launch {
//                                    try {
//                                        val task = withContext(Dispatchers.IO) {
//                                            db.collection("barbershops")
//                                                .document(it.uid)
//                                                .update("account_verification", true)
//                                                .awaitWriteWithOfflineFallback(tag = "VerifyAccountAfterLogin")
//                                        }
//
//                                        if (task.isSuccessful)
//                                            // end process without local toast checking
//                                            _registerResult.postValue(ResultState.Navigate(true, it.uid))
//                                        else {
//                                            if (task.displayMessage) _registerResult.postValue(ResultState.Failure(task.errorMessage.toString(), "UPDATE_ROLES"))
//                                            else _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
//                                        }
//                                    } catch (e: Exception) {
//                                        _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
//                                    }
//                                }
//                            } ?: run { _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES")) }
//                        } else _registerResult.postValue(ResultState.Failure("Gagal masuk dengan akun pengguna!", "UPDATE_ROLES"))
//                    }
//                } else _registerResult.postValue(ResultState.Failure("Gagal memperbarui data pengguna!", "UPDATE_ROLES"))
//            } catch (e: Exception) {
//                _registerResult.postValue(ResultState.Failure("Gagal memperbarui data pengguna!", "UPDATE_ROLES"))
//            }
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateUserRolesAndProfile() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            try {
                val targetUsername = userAdminData.username
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("official")
                        .document("barberlink2024")
                        .awaitGetWithOfflineFallback(tag = "getBarberlinkOfficialData")
                }

                if (snapshot.isSuccessful) {
                    val document = snapshot.data
                    if (document != null && document.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val listUsernames = document.get("username_list") as? List<String> ?: emptyList()
                        // Melakukan pengecekan dengan menormalisasi isi list ke lowercase
                        val isUsernameTaken = listUsernames.any { it.equals(targetUsername, ignoreCase = true) }
                        if (!isUsernameTaken) {
                            // 1. Siapkan data lokal
                            prepareUserRolesData()

                            // PENGECHECKAN MANUAL KARENA GAK OFFLINEAWARE
                            if (!NetworkMonitor.isOnline.value) {
                                val errMessage = NetworkMonitor.errorMessage.value
                                NetworkMonitor.showToast(errMessage, true)
                                _registerResult.postValue(ResultState.Failure("", "UPDATE_ROLES"))
//                                  _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "UPDATE_ROLES"))
                                return@launch
                            }

                            auth.signInWithEmailAndPassword(userAdminData.email, userAdminData.password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        task.result?.user?.let { newUser ->
                                            viewModelScope.launch { runBatchUpdate(newUser, targetUsername) }
                                        } ?: run { _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES")) }
                                    } else {
                                        _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
                                    }
                                }
                        } else {
                            _registerResult.postValue(ResultState.Failure("Username sudah ada/digunakan oleh pengguna lain.", "UPDATE_ROLES"))
                        }
                    } else {
                        if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString(), "UPDATE_ROLES"))
                        else _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
                    }
                } else {
                    if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString(), "UPDATE_ROLES"))
                    else _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
                }
            } catch (e: Exception) {
                _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
            }
        }
    }

    private suspend fun runBatchUpdate(user: FirebaseUser, targetUsername: String) {
        val success = withContext(Dispatchers.IO) {
            try {
                val batch = db.batch()
                val userRolesCopy = updateOrAddUserRoles()

                // Gunakan phone yang sesuai tipe login
                val phoneId = userAdminData.phone

                val rolesRef = db.collection("users").document(phoneId)
                batch.set(rolesRef, userRolesCopy)

                val collectionName = "barbershops"
                val profileRef = db.collection(collectionName).document(user.uid)
                batch.update(profileRef, "account_verification", true)

                val officialRef = db.collection("official").document("barberlink2024")
                batch.update(officialRef, "username_list", FieldValue.arrayUnion(targetUsername))

                batch.commit().awaitSafe()
                true
            } catch (e: Exception) {
                false
            }
        }

        if (success) _registerResult.postValue(ResultState.Navigate(true, user.uid))
        else _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
    }

    fun prepareUserRolesData() {
        userRolesData.apply {
            adminProvider = "email"
            adminRef = "barbershops/${userAdminData.uid}"
            customerProvider = if (this.role == "undefined") "none" else this.customerProvider
            customerRef = if (this.role == "undefined") "customers/${userAdminData.phone}" else this.customerRef
            uid = userAdminData.phone
        }
    }

    fun updateOrAddUserRoles(): UserRolesData {
        return userRolesData.copy().apply {
            role = when (role) {
                "" -> "admin"
                "employee" -> "pairAE"
                "pairEC(-)" -> "hybrid(-)"
                "pairEC(+)" -> "hybrid(+)"
                "customer" -> "pairAC(+)"
                else -> "pairAC(-)"
            }
        }
    }

}
