package com.example.barberlink.UserInterface.SignUp.ViewModel

import android.content.Context
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
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class StepTwoViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val context: Context
) : ViewModel() {

    private var imageUri: Uri? = null
    private var imageCopy: Uri? = null
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

    fun setImageCopy(uri: Uri?) {
        this.imageCopy = uri
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

    fun getImageCopy(): Uri? {
        return imageCopy
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

    fun checkBarbershopName(name: String, callback: (Boolean) -> Unit) {
        _registerResult.postValue(ResultState.Loading)

        db.collection("barbershops")
            .whereEqualTo("barbershop_identifier", name.replace("\\s".toRegex(), "").lowercase())
            .whereEqualTo("account_verification", true) // Tambahkan filter account_verification
            .get()
            .addOnCompleteListener { task ->
                var message = ""
                if (!task.isSuccessful) message = "Error checking barbershop name: ${task.exception?.message}"
                _registerResult.postValue(ResultState.ShowToast(message, true))
                if (task.isSuccessful) {
                    val documents = task.result.documents
                    callback(documents.isNotEmpty())
                }
            }
    }

    fun checkEmailExists(email: String, callback: (Boolean) -> Unit) {
        _registerResult.postValue(ResultState.Loading)

        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                var message = ""
                if (!task.isSuccessful) message = "Error checking email: ${task.exception?.message}"
                _registerResult.postValue(ResultState.ShowToast(message, true))
                if (task.isSuccessful) {
                    val signInMethods = task.result?.signInMethods ?: emptyList<String>()
                    callback(signInMethods.isNotEmpty())
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
        if (sameAccount) {
            _registerResult.postValue(ResultState.Loading)

            imageUri?.let {
                _registerResult.postValue(ResultState.ShowToast("Creating your Account...", false))
//                Toast.makeText(this, "Uplouding Image...", Toast.LENGTH_SHORT).show()
                // Upload image to Firebase Storage
                val storageRef = storage.reference.child("profiles/${userAdminData.uid}")
                storageRef.putFile(it)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { url ->
                            imageCopy = imageUri
                            userAdminData.imageCompanyProfile = url.toString()
                            saveNewDataAdminToFirestore()
                        }.addOnFailureListener { exception ->
                            _registerResult.postValue(ResultState.Failure("Error getting download URL: ${exception.message}", "UPLOAD_IMAGE"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        _registerResult.postValue(ResultState.Failure("Error uploading image: ${exception.message}", "UPLOAD_IMAGE"))
                    }
            } ?: also {
                saveNewDataAdminToFirestore()
            }
        } else {
            Log.d("UAD", "345")
            _registerResult.postValue(ResultState.Navigate(false, ""))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun saveNewDataAdminToFirestore() {
        // Add userAdminData to Firestore
//        Toast.makeText(this, "Create your Account...", Toast.LENGTH_SHORT).show()
//        Toast.makeText(this@SignUpStepTwo, "Please wait a moment...", Toast.LENGTH_SHORT).show()
        db.collection("barbershops")
            .document(userAdminData.uid)
            .set(userAdminData)
            .addOnSuccessListener {
                userAdminCopy = userAdminData.copy()
                clearOutletsAndAddNew()
            }.addOnFailureListener { exception ->
                _registerResult.postValue(ResultState.Failure("Error saving data: ${exception.message}", "SAVE_DATA"))
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
//                Toast.makeText(this@SignUpStepTwo, "Please wait a moment...", Toast.LENGTH_SHORT).show()
//                Toast.makeText(this@SignUpStepTwo, "Data Synchronization...", Toast.LENGTH_SHORT).show()
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
                    // Run updateUserRolesAndProfile if both are successful
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

    private fun updateUserRoles(): Task<Void> {
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
        val allTasks = mutableListOf(updateUserRoles()) // List untuk menampung semua task

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
                // Log in the user after both updates are successful
                if (!NetworkMonitor.isOnline.value) {
                    val errMessage = NetworkMonitor.errorMessage.value
                    NetworkMonitor.showToast(errMessage, true)

                    _registerResult.postValue(ResultState.Failure("", "UPDATE_ROLES"))
//                    _registerResult.postValue(ResultState.Failure("Koneksi internet tidak tersedia. Periksa koneksi Anda.", "UPDATE_ROLES"))
                    return@addOnSuccessListener
                }
//                Toast.makeText(this@SignUpStepTwo, "Please wait a moment...", Toast.LENGTH_SHORT).show()

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
                                    // Navigasi ke halaman SuccessPage setelah update berhasil
                                    _registerResult.postValue(ResultState.Navigate(true, user.uid))
                                }
                                .addOnFailureListener {
                                    // Handle jika gagal mengupdate account_verification
                                    _registerResult.postValue(
                                        ResultState.Failure(
                                            "Gagal melakukan verifikasi akun pengguna!",
                                            "UPDATE_ROLES"
                                        )
                                    )
                                }
                        }
                    } else {
                        _registerResult.postValue(
                            ResultState.Failure(
                                "Login failed: ${task.exception?.message}",
                                "UPDATE_ROLES"
                            )
                        )
                    }
                }
//                InternetCheck { internet ->
//                    if (internet) {
//
//                    } else {
//                        _registerResult.postValue(ResultState.Failure("Koneksi internet tidak stabil. Periksa koneksi Anda.", "UPDATE_ROLES"))
//                    }
//                }.execute() // Pastikan untuk mengeksekusi AsyncTask
            }
            .addOnFailureListener { exception ->
                _registerResult.postValue(ResultState.Failure("Error updating data: ${exception.message}", "UPDATE_ROLES"))
            }
    }


}