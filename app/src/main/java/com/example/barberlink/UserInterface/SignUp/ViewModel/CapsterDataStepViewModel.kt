package com.example.barberlink.UserInterface.SignUp.ViewModel

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserEmployeeData
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class CapsterDataStepViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : ViewModel() {

    private var imageUri: Uri? = null
    private var imageCopy: Uri? = null
    private var userInputGender: String = "Rahasiakan"
    private lateinit var userEmployeeData: UserEmployeeData
    private lateinit var userRolesData: UserRolesData
    private var userEmployeeCopy: UserEmployeeData = UserEmployeeData()
    private var existingEmployeeData: UserEmployeeData? = null
    private var useEmployeeUsernameNotVerified: Boolean = false
    private var useEmployeeEmailAnExisting: Boolean = false

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

    fun setUserInputGender(gender: String) {
        viewModelScope.launch {
            userInputGender = gender
        }
    }

    fun setUserEmployeeData(data: UserEmployeeData) {
        viewModelScope.launch {
            userEmployeeData = data
        }
    }

    fun setUserRolesData(data: UserRolesData) {
        viewModelScope.launch {
            userRolesData = data
        }
    }

    fun setUserEmployeeCopy(data: UserEmployeeData) {
        viewModelScope.launch {
            userEmployeeCopy = data
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

    fun getUserInputGender(): String {
        return runBlocking {
            userInputGender
        }
    }

    fun getUserEmployeeData(): UserEmployeeData {
        return runBlocking {
            userEmployeeData
        }
    }

    fun getUserRolesData(): UserRolesData {
        return runBlocking {
            userRolesData
        }
    }

    fun getUserEmployeeCopy(): UserEmployeeData {
        return runBlocking {
            userEmployeeCopy
        }
    }

    fun getExistingEmployeeData(): UserEmployeeData? {
        return runBlocking {
            existingEmployeeData
        }
    }

    fun getUserShouldReAuthenticate(): Boolean {
        return runBlocking {
            useEmployeeUsernameNotVerified && useEmployeeEmailAnExisting
        }
    }

    fun checkEmployeeIdentifier(username: String, callback: (Boolean) -> Unit) {
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
                val formattedUsername = username.replace("\\s".toRegex(), "").lowercase().trim()
                Logger.d("CheckEmployeeIdentifier",
                    "formattedName: $formattedUsername"
                )
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("employees")
                        .whereEqualTo("username", formattedUsername)
                        .awaitGetWithOfflineFallback(tag = "CheckEmployeeUsername")
                }

                var message = ""
                if (!snapshot.isSuccessful) message = if (snapshot.displayMessage) snapshot.errorMessage.toString() else "Gagal memeriksa username pegawai!"
                _registerResult.value = ResultState.ShowToast(message, true)
                Logger.d("CheckEmployeeIdentifier",
                    "employeeName: ${snapshot.data?.documents?.firstOrNull()?.toObject(UserEmployeeData::class.java)?.fullname ?: "null"}"
                )
                Logger.d("CheckEmployeeIdentifier",
                    "size: ${snapshot.data?.documents?.size ?: "null"}"
                )
                if (snapshot.isSuccessful) {
                    existingEmployeeData = snapshot.data?.documents?.firstOrNull()?.toObject(UserEmployeeData::class.java)
                    useEmployeeUsernameNotVerified = existingEmployeeData?.accountVerification == false
                    if (useEmployeeUsernameNotVerified) {
                        callback(false)
                        return@launch
                    }
                    val document = snapshot.data?.documents as? List<*> ?: emptyList<String>()
                    callback(document.isNotEmpty())
                }
            } catch (e: Exception) {
                _registerResult.value = ResultState.ShowToast("Gagal memeriksa username pegawai!", true)
            }
        }
    }

    fun checkUsernameEmployee(targetUsername: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            // CHECK APAKAH USERNAME PEGAWAI SUDAH TERDAFTAR
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
                        if (!task.isSuccessful) message = "Gagal memeriksa email pegawai!"

                        _registerResult.value = ResultState.ShowToast(message, true)
                        if (task.isSuccessful) {
                            val signInMethods = task.result?.signInMethods ?: emptyList<String>()
                            val emailIsExist = signInMethods.isNotEmpty()
                            if (useEmployeeUsernameNotVerified && emailIsExist && email == existingEmployeeData?.email) {
                                useEmployeeEmailAnExisting = true
                                callback(false)
                                return@addOnCompleteListener
                            }
                            callback(signInMethods.isNotEmpty())
                        }
                    }
            } catch (e: Exception) {
                _registerResult.value = ResultState.ShowToast("Gagal memeriksa email pegawai!", true)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun addNewUserEmployeeToDatabase(sameAccount: Boolean) {
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

                _registerResult.value = ResultState.ShowToast("Membuat akun pegawai!", false)

                imageUri?.let {
//                Toast.makeText(this, "Uplouding Image...", Toast.LENGTH_SHORT).show()
                    // Upload image to Firebase Storage
                    try {
                        val storageRef = storage.reference.child("profiles/${userEmployeeData.uid}")
                        storageRef.putFile(it)
                            .addOnSuccessListener {
                                storageRef.downloadUrl.addOnSuccessListener { url ->
                                    imageCopy = imageUri
                                    userEmployeeData.photoProfile = url.toString()
                                    saveNewDataEmployeeToFirestore()
                                }.addOnFailureListener { exception ->
                                    _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat mengunggah profil pegawai!", "UPLOAD_IMAGE"))
                                }
                            }.addOnFailureListener { exception ->
                                _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat mengunggah profil pegawai!", "UPLOAD_IMAGE"))
                            }
                    } catch (e: Exception) {
                        _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat mengunggah profil pegawai!", "UPLOAD_IMAGE"))
                    }
                } ?: saveNewDataEmployeeToFirestore()
            } else {
                Log.d("UAD", "345")
                _registerResult.postValue(ResultState.Navigate(false, ""))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun saveNewDataEmployeeToFirestore() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            try {
                userEmployeeData.talentAvailability = true
                val task = withContext(Dispatchers.IO) {
                    db.collection("employees")
                        .document(userEmployeeData.uid)
                        .set(userEmployeeData)
                        .awaitWriteWithOfflineFallback(tag = "SaveNewEmployeeData")
                }

                if (task.isSuccessful) {
                    // not end process
                    userEmployeeData.userRef = "employees/${userEmployeeData.uid}"
                    userEmployeeCopy = userEmployeeData.copy()
                    updateUserRolesAndProfile()
                } else {
                    if (task.displayMessage) _registerResult.postValue(ResultState.Failure(task.errorMessage.toString(), "SAVE_DATA"))
                    else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat menyimpan data pegawai!", "SAVE_DATA"))
                }
            } catch (e:  Exception) {
                _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat menyimpan data pegawai!", "SAVE_DATA"))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateUserRolesAndProfile() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)
            try {
                val targetUsername = userEmployeeData.username
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

                            Logger.d("CheckBon", "email: ${userEmployeeData.email} | password: ${userEmployeeData.password}")
                            auth.signInWithEmailAndPassword(userEmployeeData.email, userEmployeeData.password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        task.result?.user?.let { newUser ->
                                            viewModelScope.launch { runBatchUpdate(newUser, targetUsername) }
                                        } ?: run {
                                            Logger.d("CheckBon", "Sign-in failed for email: ${userEmployeeData.email} | error: User object is null after successful sign-in")
                                            _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
                                        }
                                    } else {
                                        Logger.d("CheckBon", "Sign-in failed for email: ${userEmployeeData.email} | error: ${task.exception?.message}")
                                        _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
                                    }
                                }
                        } else {
                            Logger.d("CheckBon", "Username check failed: '$targetUsername' is already taken (case-insensitive match found)")
                            _registerResult.postValue(ResultState.Failure("Username sudah ada/digunakan oleh pengguna lain.", "UPDATE_ROLES"))
                        }
                    } else {
                        Logger.d("CheckBon", "Failed to retrieve official data for username check: Document does not exist")
                        if (snapshot.displayMessage) _registerResult.postValue(
                            ResultState.Failure(snapshot.errorMessage.toString(), "UPDATE_ROLES"))
                        else _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
                    }
                } else {
                    Logger.d("CheckBon", "Failed to retrieve official data for username check: ${snapshot.errorMessage}")
                    if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString(), "UPDATE_ROLES"))
                    else _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))
                }
            } catch (e: Exception) {
                Logger.d("CheckBon", "Exception during updateUserRolesAndProfile: ${e.message}")
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
                val phoneId = userEmployeeData.phone

                val rolesRef = db.collection("users").document(phoneId)
                batch.set(rolesRef, userRolesCopy)

                val collectionName = "employees"
                val profileRef = db.collection(collectionName).document(user.uid)
                batch.update(profileRef, "account_verification", true)

                val officialRef = db.collection("official").document("barberlink2024")
                batch.update(officialRef, "username_list", FieldValue.arrayUnion(targetUsername))

                try {
                    Logger.d("CheckBon", "Melakukan commit batch...")
                    batch.commit().awaitSafe()
                    Logger.d("CheckBon", "Commit batch berhasil")
                    true
                } catch (e: Exception) {
                    Logger.d("CheckBon", "GAGAL: Commit batch - ${e.message}")
                    false
                }
            } catch (e: Exception) {
                Logger.d("CheckBon", "GAGAL: Menyiapkan batch update - ${e.message}")
                false
            }
        }

        if (success) _registerResult.postValue(ResultState.Navigate(true, user.uid))
        else _registerResult.postValue(ResultState.Failure("Gagal memverifikasi akun pengguna!", "UPDATE_ROLES"))

    }

    fun prepareUserRolesData() {
        userRolesData.apply {
            employeeProvider = "email"
            employeeRef = "employees/${userEmployeeData.uid}"
            customerProvider = if (this.role == "undefined") "none" else this.customerProvider
            customerRef = if (this.role == "undefined") "customers/${userEmployeeData.phone}" else this.customerRef
            uid = userEmployeeData.phone
        }
    }

    fun updateOrAddUserRoles(): UserRolesData {
        return userRolesData.copy().apply {
            role = when (role) {
                "" -> "employee"
                "admin" -> "pairAE"
                "pairAC(-)" -> "hybrid(-)"
                "pairAC(+)" -> "hybrid(+)"
                "customer" -> "pairEC(+)"
                else -> "pairEC(-)"
            }
        }
    }

}
