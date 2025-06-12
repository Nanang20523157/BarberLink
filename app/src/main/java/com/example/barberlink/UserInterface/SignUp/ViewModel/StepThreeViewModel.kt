package com.example.barberlink.UserInterface.SignUp.ViewModel

import android.content.Context
import android.net.Uri
import android.os.Build
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
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class StepThreeViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val context: Context
) : ViewModel() {

    private var imageUri: Uri? = null
    private lateinit var userAdminData: UserAdminData
    private lateinit var userRolesData: UserRolesData
    private var userAdminCopy: UserAdminData = UserAdminData()

    private val _registerResult = MutableLiveData<ResultState?>()
    val registerResult: LiveData<ResultState?> = _registerResult

    sealed class ResultState {
        data object Loading : ResultState()
        data class Navigate(val isAddData: Boolean, val uid: String): ResultState()
        data class Failure(val message: String, val step: String) : ResultState()
        data class ShowToast(val message: String, val hideLoading: Boolean) : ResultState()
    }

    fun setRegisterResult(value: ResultState?) {
        _registerResult.value = value
    }

    fun setImageUri(uri: Uri?) {
        this.imageUri = uri
    }

    fun setUserAdminData(data: UserAdminData) {
        this.userAdminData = data
    }

    fun setUserRolesData(data: UserRolesData) {
        this.userRolesData = data
    }

    fun setUserAdminCopy(data: UserAdminData) {
        this.userAdminCopy = data
    }

    fun getImageUri(): Uri? {
        return imageUri
    }

    fun getUserAdminData(): UserAdminData {
        return userAdminData
    }

    fun getUserRolesData(): UserRolesData {
        return userRolesData
    }

    fun getUserAdminCopy(): UserAdminData {
        return userAdminData
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
    fun createNewAccount(email: String, password: String) {
        if (!NetworkMonitor.isOnline.value) {
            val errMessage = NetworkMonitor.errorMessage.value
            NetworkMonitor.showToast(errMessage, true)

            _registerResult.postValue(ResultState.ShowToast("", false))
//            _registerResult.postValue(ResultState.ShowToast("Koneksi internet tidak tersedia. Periksa koneksi Anda.", false))
            return
        }

        _registerResult.postValue(ResultState.Loading)
        _registerResult.postValue(ResultState.ShowToast("Prepare the necessary data...", false))
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    //val user = task.result?.user
                    userAdminCopy = userAdminData.copy()
                    if (user != null) {
                        userAdminData.uid = user.uid
                        addNewUserAdminToDatabase()
                    } else {
                        _registerResult.postValue(ResultState.Failure("Gagal mengambil kembali informasi akun pengguna.", "RETRIEVE_UID"))
                    }
                } else {
                    _registerResult.postValue(ResultState.ShowToast( "Error creating account: ${task.exception?.message}", true))
                }
            }
        // Cek apakah koneksi internet benar-benar dapat mengakses server
//        InternetCheck { internet ->
//            if (internet) {
//
//            } else {
//                _registerResult.postValue(ResultState.ShowToast("Koneksi internet tidak stabil. Periksa koneksi Anda.", true))
//            }
//        }.execute() // Pastikan untuk mengeksekusi AsyncTask
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun getUserUidFromAuth(email: String, password: String) {
        if (!NetworkMonitor.isOnline.value) {
            val errMessage = NetworkMonitor.errorMessage.value
            NetworkMonitor.showToast(errMessage, true)

            _registerResult.postValue(ResultState.Failure("", "RETRIEVE_UID"))
//            _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "RETRIEVE_UID"))
            return
        }

        _registerResult.postValue(ResultState.Loading)
        _registerResult.postValue(ResultState.ShowToast("Prepare the necessary data...", false))
        if ((userAdminCopy.email == userAdminData.email) && (userAdminCopy.password == userAdminData.password)) {
            // Login ulang untuk mendapatkan token pengguna
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            userAdminData.uid = user.uid
                            addNewUserAdminToDatabase()
                        } else {
                            _registerResult.postValue(ResultState.Failure("Gagal mengambil kembali informasi akun pengguna.", "RETRIEVE_UID"))
                        }
                    } else {
                        _registerResult.postValue(ResultState.Failure("Error signing in: ${signInTask.exception?.message}", "RETRIEVE_UID"))
                    }
                }
        } else {
            val user = auth.currentUser
            val credential = user?.email?.let { EmailAuthProvider.getCredential(it, userAdminCopy.password) }
            // Re-authenticate the user with the current password
            if (credential != null) {
                user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
                    if (reAuthTask.isSuccessful) {
                        // Update the password
                        user.updatePassword(password).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                userAdminCopy = userAdminData.copy()
                                addNewUserAdminToDatabase()
                            } else {
                                _registerResult.postValue(ResultState.Failure("Failed to update account: ${updateTask.exception?.message}", "RETRIEVE_UID"))
                            }
                        }
                    } else {
                        _registerResult.postValue(ResultState.Failure("Re-authentication failed: ${reAuthTask.exception?.message}", "RETRIEVE_UID"))
                    }
                }
            }
        }
        // Cek apakah koneksi internet benar-benar dapat mengakses server
//        InternetCheck { internet ->
//            if (internet) {
//
//            } else {
//                _registerResult.postValue(ResultState.Failure("Koneksi internet tidak stabil. Periksa koneksi Anda.", "RETRIEVE_UID"))
//            }
//        }.execute() // Pastikan untuk mengeksekusi AsyncTask
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun addNewUserAdminToDatabase() {
        imageUri?.let {
            _registerResult.postValue(ResultState.ShowToast("Creating your Account...", false))
//            Toast.makeText(this, "Uploading Image...", Toast.LENGTH_SHORT).show()
            val storageRef = storage.reference.child("profiles/${userAdminData.uid}")
            storageRef.putFile(it)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { url ->
                        userAdminData.imageCompanyProfile = url.toString()
                        saveNewDataAdminToFirestore()
                    }.addOnFailureListener { exception ->
                        _registerResult.postValue(ResultState.Failure("Error getting download URL: ${exception.message}", "UPLOAD_IMAGE"))
                    }
                }.addOnFailureListener { exception ->
                    _registerResult.postValue(ResultState.Failure( "Error uploading image: ${exception.message}", "UPLOAD_IMAGE"))
                }
        } ?: saveNewDataAdminToFirestore()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun saveNewDataAdminToFirestore() {
//        Toast.makeText(this, "Create your Account...", Toast.LENGTH_SHORT).show()
//        Toast.makeText(this, "Please wait a moment...", Toast.LENGTH_SHORT).show()
        db.collection("barbershops")
            .document(userAdminData.uid)
            .set(userAdminData)
            .addOnSuccessListener {
                clearOutletsAndAddNew()
            }.addOnFailureListener { exception ->
                _registerResult.postValue(ResultState.Failure("Error saving document: ${exception.message}", "SAVE_DATA"))
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun clearOutletsAndAddNew() {
        val outletsCollection = db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("outlets")

        // Get all documents in the "outlets" sub-collection
        outletsCollection.get()
            .addOnSuccessListener { querySnapshot ->
//                Toast.makeText(this, "Please wait a moment...", Toast.LENGTH_SHORT).show()
//                Toast.makeText(this@SignUpStepThree, "Data Synchronization...", Toast.LENGTH_SHORT).show()
                if (querySnapshot.isEmpty) {
                    // No documents found, add new outlet and default service directly
                    runAddOutletAndService()
                } else {
                    val batch = db.batch()
                    // Delete each document in the "outlets" sub-collection
                    for (document in querySnapshot.documents) {
                        batch.delete(document.reference)
                    }
                    // Commit the batch
                    batch.commit().addOnSuccessListener {
                        // Add the new outlet and default service after deleting old ones
                        runAddOutletAndService()
                    }.addOnFailureListener { exception ->
                        _registerResult.postValue(ResultState.Failure("Error committing batch delete: ${exception.message}", "BATCH_DELETE"))
                    }
                }
            }
            .addOnFailureListener { exception ->
                _registerResult.postValue(ResultState.Failure("Error clearing outlets: ${exception.message}", "BATCH_DELETE"))
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun runAddOutletAndService() {
        viewModelScope.launch(Dispatchers.IO) {
            val taskFailed = AtomicBoolean(false)
            val addOutletJob = async {
                val prosesStatus = addOutletDataBarbershopAsync()
                if (prosesStatus) { taskFailed.set(true) }
            }
            val addServiceJob = async {
                val prosesStatus = addDefaultItemServiceAsync()
                if (prosesStatus) { taskFailed.set(true) }
            }

            try {
                // Wait for both tasks to complete
                addOutletJob.await()
                addServiceJob.await()

                if (taskFailed.get()) {
                    withContext(Dispatchers.Main) {
                        _registerResult.postValue(ResultState.Failure("Gagal menambahkan data yang dibutuhkan", "ADD_SUPPORT_DATA"))
                    }
                } else {
                    // Run updateOrAddUserRoles if both are successful
                    updateUserRolesAndProfile()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _registerResult.postValue(ResultState.Failure("Error running tasks: ${e.message}", "ADD_SUPPORT_DATA"))
                }
                throw e
            }
        }
    }

    private suspend fun addOutletDataBarbershopAsync(): Boolean {
        val isFailed = AtomicBoolean(false)
        val uidOutlet = userAdminData.barbershopIdentifier + "01"
        val outletData = Outlet(
            uid = uidOutlet,
            outletName = userAdminData.barbershopName + " 01",
            outletPhoneNumber = userAdminData.phone,
            rootRef = "barbershops/${userAdminData.uid}",
            listServices = mutableListOf("BSoBVRz4H5wkAeppmTJw")
        )
        db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("outlets")
            .document(uidOutlet)
            .set(outletData)
            .addOnFailureListener { isFailed.set(true) }
            .await() // Convert to coroutine-friendly await

        return isFailed.get()
    }

    private suspend fun addDefaultItemServiceAsync(): Boolean {
        val isFailed = AtomicBoolean(false)
        val defaultService = Service(
            applyToGeneral = true,
            autoSelected = true,
            categoryDetail = "VOeNhb893iaDpKdaICOD",
            defaultItem = true,
            freeOfCharge = true,
            resultsShareAmount = mapOf("all" to 0),
            rootRef = "barbershops/${userAdminData.uid}",
            serviceCategory = "Conversation",
            serviceCounting = 0,
            serviceDesc = "Hair Care and Consultation adalah layanan komprehensif yang menghadirkan Tim ahli kami untuk memberikan edukasi mengenai perawatan rambut yang sesuai dengan kebutuhan spesifik Anda, mulai dari pembersihan, perawatan kulit kepala, hingga pemilihan produk perawatan yang tepat. Selain itu, kami juga menawarkan konsultasi mendalam untuk membantu Anda memahami kondisi rambut Anda dan memberikan rekomendasi terbaik untuk perawatan lanjutan.",
            serviceIcon = "https://firebasestorage.googleapis.com/v0/b/barberlink-bfb66.appspot.com/o/services%2Ficons%2FBSoBVRz4H5wkAeppmTJw.png?alt=media&token=3c3f9c48-5368-4507-bc65-1da71b0d1ab3",
            serviceImg = "https://firebasestorage.googleapis.com/v0/b/barberlink-bfb66.appspot.com/o/services%2Fimages%2FBSoBVRz4H5wkAeppmTJw.png?alt=media&token=958c06d4-14f5-42f8-a912-410ede1aa6e7",
            serviceName = "Hair Care and Consultation",
            servicePrice = 0,
            serviceRating = 4.5,
            uid = "BSoBVRz4H5wkAeppmTJw",
        )

        db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("services")
            .document(defaultService.uid)
            .set(defaultService)
            .addOnFailureListener { isFailed.set(true) }
            .await() // Convert to coroutine-friendly await

        return isFailed.get()
    }

    private fun updateCustomerPhotoProfile(): Task<Void> {
        // profilenya mau disamakan atau enggak?
        val updates = hashMapOf<String, Any?>(
            "photo_profile" to userAdminData.imageCompanyProfile
        )

        return db.document(userRolesData.customerRef)
            .update(updates)
    }

    private fun updateEmployeePhotoProfile(): Task<Void> {
        // profilenya mau disamakan atau enggak?
        val updates = hashMapOf<String, Any?>(
            "photo_profile" to userAdminData.imageCompanyProfile
        )

        return db.document(userRolesData.employeeRef)
            .update(updates)
    }

    private fun updateOrAddUserRoles(): Task<Void> {
        val userRolesCopy = userRolesData.copy().apply {
            role = when (role) {
                "" -> "admin"
                "employee" -> "pairAE"
                "pairEC(-)" -> "hybrid(-)"
                "pairEC(+)" -> "hybrid(+)"
                "customer" -> "pairAC(+)"
                else -> "pairAC(-)"
            }
        }

        return db.collection("users")
            .document(userAdminData.phone)
            .set(userRolesCopy)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateUserRolesAndProfile() {
        userRolesData.apply {
            adminProvider = "email"
            adminRef = "barbershops/${userAdminData.uid}"
            customerProvider = if (this.role == "undefined") "none" else this.customerProvider
            customerRef = if (this.role == "undefined") "customers/${userAdminData.phone}" else this.customerRef
            uid = userAdminData.phone
        }
        val allTasks = mutableListOf(updateOrAddUserRoles()) // List untuk menampung semua task

        when (userRolesData.role) {
            "" -> {
                // Tidak melakukan update profile
            }
            "employee" -> {
                allTasks.add(updateEmployeePhotoProfile())
            }
            "pairEC(-)" -> {
                allTasks.add(updateEmployeePhotoProfile())
                allTasks.add(updateCustomerPhotoProfile())
            }
            "pairEC(+)" -> {
                allTasks.add(updateEmployeePhotoProfile())
                allTasks.add(updateCustomerPhotoProfile())
            }
            "customer" -> {
                allTasks.add(updateCustomerPhotoProfile())
            }
            else -> {
                allTasks.add(updateCustomerPhotoProfile())
            }
        }

        Tasks.whenAllSuccess<Void>(allTasks)
            .addOnSuccessListener {
//                Toast.makeText(this, "Please wait a moment...", Toast.LENGTH_SHORT).show()

                if (auth.currentUser != null) {
                    val user = auth.currentUser
                    user?.let {
                        // Update account_verification to true
                        db.collection("barbershops")
                            .document(it.uid)
                            .update("account_verification", true)
                            .addOnSuccessListener {
                                _registerResult.postValue(ResultState.Navigate(true, user.uid))
                            }
                            .addOnFailureListener { _ ->
                                _registerResult.postValue(ResultState.Failure("Gagal melakukan verifikasi akun pengguna!", "UPDATE_ROLES"))
                            }
                    }
                } else {
                    if (!NetworkMonitor.isOnline.value) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)

                        _registerResult.postValue(ResultState.Failure("", "UPDATE_ROLES"))
//                        _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "UPDATE_ROLES"))
                        return@addOnSuccessListener
                    }

                    auth.signInWithEmailAndPassword(
                        userAdminData.email,
                        userAdminData.password
                    ).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.let {
                                // Update account_verification to true
                                db.collection("barbershops")
                                    .document(it.uid)
                                    .update("account_verification", true)
                                    .addOnSuccessListener {
                                        _registerResult.postValue(ResultState.Navigate(true, user.uid))
                                    }
                                    .addOnFailureListener {
                                        _registerResult.postValue(ResultState.Failure("Gagal melakukan verifikasi akun pengguna!", "UPDATE_ROLES"))
                                    }
                            }
                        } else {
                            _registerResult.postValue(ResultState.Failure("Login failed: ${task.exception?.message}", "UPDATE_ROLES"))
                        }
                    }
                    // Cek apakah koneksi internet benar-benar dapat mengakses server
//                    InternetCheck { internet ->
//                        if (internet) {
//
//                        } else {
//                            _registerResult.postValue(ResultState.Failure("Koneksi internet tidak stabil. Periksa koneksi Anda.", "UPDATE_ROLES"))
//                        }
//                    }.execute() // Pastikan untuk mengeksekusi AsyncTask
                }
            }
            .addOnFailureListener { exception ->
                _registerResult.postValue(ResultState.Failure("Error updating data: ${exception.message}", "UPDATE_ROLES"))
            }
    }

}