package com.example.barberlink.UserInterface.SignUp.Fragment

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.barberlink.Helper.PermissionHelper.showRationaleDialog
import com.example.barberlink.Helper.PermissionHelper.showSettingsDialog
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.databinding.FragmentImagePickerBinding
import com.github.dhaval2404.imagepicker.ImagePicker

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ImagePickerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ImagePickerFragment : DialogFragment() {
    private var _binding: FragmentImagePickerBinding? = null
    private val debounce by lazy { ScopedUniversalDebounce() }
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private var permissionRequestStartTime: Long = 0
    private var wasRationaleRequiredBefore: Boolean = false
    private var wasGalleryRationaleRequiredBefore: Boolean = false

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCameraPicker()
        } else {
            val duration = System.currentTimeMillis() - permissionRequestStartTime
            val newRationaleState = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            val isRationaleStateChanged = wasRationaleRequiredBefore != newRationaleState

            if (isRationaleStateChanged) {
                if (newRationaleState) {
                    showRationaleDialog(
                        requireContext(),
                        "Izin Kamera Dibutuhkan",
                        "Aplikasi membutuhkan akses kamera untuk mengambil foto profil atau bukti transaksi Anda."
                    ) {
                        requestCameraPermission()
                    }
                } else {
                    showSettingsDialog(
                        requireContext(),
                        "Izin Kamera Permanen Ditolak",
                        "Anda telah menolak izin kamera secara permanen. Silakan aktifkan manual di pengaturan agar fitur kamera dapat digunakan."
                    )
                }
            } else {
                if (duration < 300) {
                    showSettingsDialog(
                        requireContext(),
                        "Izin Kamera Permanen Ditolak",
                        "Anda telah menolak izin kamera secara permanen. Silakan aktifkan manual di pengaturan agar fitur kamera dapat digunakan."
                    )
                }
            }
        }
    }

    private fun requestCameraPermission() {
        permissionRequestStartTime = System.currentTimeMillis()
        wasRationaleRequiredBefore = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGalleryPicker()
        } else {
            val duration = System.currentTimeMillis() - permissionRequestStartTime
            val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            val newRationaleState = shouldShowRequestPermissionRationale(galleryPermission)
            val isRationaleStateChanged = wasGalleryRationaleRequiredBefore != newRationaleState

            if (isRationaleStateChanged) {
                if (newRationaleState) {
                    showRationaleDialog(
                        requireContext(),
                        "Izin Galeri Dibutuhkan",
                        "Aplikasi membutuhkan akses galeri untuk memilih foto profil Anda."
                    ) {
                        requestGalleryPermission()
                    }
                } else {
                    showSettingsDialog(
                        requireContext(),
                        "Izin Galeri Permanen Ditolak",
                        "Anda telah menolak izin galeri secara permanen. Silakan aktifkan manual di pengaturan agar dapat memilih foto dari galeri."
                    )
                }
            } else {
                if (duration < 300) {
                    showSettingsDialog(
                        requireContext(),
                        "Izin Galeri Permanen Ditolak",
                        "Anda telah menolak izin galeri secara permanen. Silakan aktifkan manual di pengaturan agar dapat memilih foto dari galeri."
                    )
                }
            }
        }
    }

    private fun requestGalleryPermission() {
        permissionRequestStartTime = System.currentTimeMillis()
        val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        wasGalleryRationaleRequiredBefore = shouldShowRequestPermissionRationale(galleryPermission)
        requestGalleryPermissionLauncher.launch(galleryPermission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentImagePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            dismiss() // Close the dialog when ivBack is clicked
        }

        binding.btnCamera.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            openCameraPicker()
        }

        binding.btnGallery.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            openGalleryPicker()
        }
    }

    private fun openCameraPicker() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        ImagePicker.with(this)
            .cameraOnly()
            .crop()
            .compress(1024)
            .maxResultSize(1080, 1080)
            .start(IMAGE_PICKER_REQUEST_CODE)
    }

    private fun openGalleryPicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val galleryPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), galleryPermission) != PackageManager.PERMISSION_GRANTED) {
                requestGalleryPermission()
                return
            }
        }

        ImagePicker.with(this)
            .galleryOnly()
            .crop()
            .compress(1024)
            .maxResultSize(1080, 1080)
            .start(IMAGE_PICKER_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == IMAGE_PICKER_REQUEST_CODE) {
            val uri = data?.data
            if (uri != null) {
                val result = Bundle().apply {
                    putString("image_uri", uri.toString())
                }
                parentFragmentManager.setFragmentResult("image_picker_request", result)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val IMAGE_PICKER_REQUEST_CODE = 1001
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ImagePickerFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String? = null, param2: String? = null): ImagePickerFragment {
            return ImagePickerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
        }
    }
}
