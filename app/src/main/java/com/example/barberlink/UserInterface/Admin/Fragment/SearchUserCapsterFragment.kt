package com.example.barberlink.UserInterface.Admin.Fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.toColorInt
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.Repository.EmployeeRepository
import com.example.barberlink.UserInterface.Admin.AddEmployeeFormActivity
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageEmployeeViewModel
import com.example.barberlink.databinding.FragmentSearchUserCapsterBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class SearchUserCapsterFragment : DialogFragment() {

    private var _binding: FragmentSearchUserCapsterBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val searchCapsterViewModel: ManageEmployeeViewModel by activityViewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private lateinit var context: Context
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private val binding get() = _binding!!

    private var matchedEmployee: UserEmployeeData? = null
    private var searchMessage: String = ""
    private var isEligible: Boolean = false
    // Keyword terakhir yang berhasil di-search (untuk mendeteksi perubahan ketikan)
    private var lastSearchedUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchCapsterViewModel
//        setStyle(STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
//        arguments?.let {
//            userAdminData = it.getParcelable("userAdminData")
//            val rolesParcelable = it.getParcelableArray("employeeRoles")
//            employeeRoles = rolesParcelable?.map { it as EmployeeRolesData }?.toTypedArray()
//            val empParcelable = it.getParcelableArray("employeeList")
//            employeeList = empParcelable?.map { it as UserEmployeeData }?.toTypedArray()
//            val outletParcelable = it.getParcelableArray("outletList")
//            outletList = outletParcelable?.map { it as Outlet }?.toTypedArray()
//            isDirectAdd = it.getBoolean("isDirectAdd", false)
//        }
        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchUserCapsterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("matched_employee", matchedEmployee)
        outState.putString("search_message", searchMessage)
        outState.putBoolean("is_eligible", isEligible)
        outState.putString("last_searched_username", lastSearchedUsername)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Panggil fungsi pertama kali
        updateMargins()

        // Deteksi perubahan orientasi layar
        val listener = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                updateMargins()
            }
        }

        viewLifecycleOwner.lifecycle.addObserver(listener)

        // Simpan listener agar bisa dihapus nanti jika perlu
        this.lifecycleListener = listener

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Jangan dismiss dialog jika area cdCapitalForm yang diklik
                if (isTouchOnForm(e)) {
                    return false  // Jangan lanjutkan dismiss
                }

                Log.d("ListQueueBoardFragment", "Background scrim clicked")
                setFragmentResult("action_dissmis_dialog", bundleOf(
                    "dismiss_dialog" to true
                ))

                dismiss()
                parentFragmentManager.popBackStack()
                return true
            }
        })

        binding.nvBackgroundScrim.setOnTouchListener { view, event ->
            if (gestureDetector.onTouchEvent(event)) {
                // Deteksi klik dan panggil performClick untuk aksesibilitas
                view.performClick()
                true
            } else {
                // Teruskan event ke sistem untuk menangani scroll/swipe
                false
            }
        }

        // Hide profile card initially
        binding.tvUsername.text = "???"
        binding.tvRole.text = "    ???    "
        binding.tvReviewsAmount.text = "?? Reviews"
        binding.btnSeeDetail.isEnabled = false

        // Set initial message for tvSearchingResult
        val initialPromptMessage = "Masukkan data username pegawai yang anda cari!"
        binding.tvSearchingResult.text = initialPromptMessage
        binding.tvSearchingResult.setTextColor(
            ContextCompat.getColor(context, R.color.grey_text_wa)
        )

        // TextWatcher: deteksi perubahan ketikan setelah search
        binding.etUsername.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Hanya aktif jika ada hasil search sebelumnya (lastSearchedUsername tidak kosong)
                if (lastSearchedUsername.isEmpty()) return

                val currentInput = s?.toString() ?: ""
                when {
                    // User ketik kembali persis sama dengan keyword lama → tampilkan hasil search sebelumnya
                    currentInput == lastSearchedUsername -> {
                        binding.tvSearchingResult.text = searchMessage
                        if (isEligible) {
                            binding.tvSearchingResult.setTextColor(
                                ContextCompat.getColor(context, R.color.grey_text_wa)
                            )
                            // Kembalikan button ke state eligible
                            binding.btnSeeDetail.isEnabled = true
                            binding.btnSeeDetail.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black)
                            binding.btnSeeDetail.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.btnSeeDetail.setTextColor(resources.getColor(R.color.green_lime_wf))
                        } else {
                            binding.tvSearchingResult.setTextColor(
                                ContextCompat.getColor(context, R.color.magenta)
                            )
                            // Kembalikan button ke state not eligible
                            binding.btnSeeDetail.isEnabled = false
                            binding.btnSeeDetail.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.disable_grey_background)
                            binding.btnSeeDetail.setTypeface(null, android.graphics.Typeface.NORMAL)
                            binding.btnSeeDetail.setTextColor(resources.getColor(R.color.white))
                        }
                    }
                    // User mengetik sesuatu yang berbeda → kembalikan ke pesan awal + disable button
                    else -> {
                        binding.tvSearchingResult.text = initialPromptMessage
                        binding.tvSearchingResult.setTextColor(
                            ContextCompat.getColor(context, R.color.grey_text_wa)
                        )
                        // Disable button saat mengetik keyword baru
                        binding.btnSeeDetail.isEnabled = false
                        binding.btnSeeDetail.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.disable_grey_background)
                        binding.btnSeeDetail.setTypeface(null, android.graphics.Typeface.NORMAL)
                        binding.btnSeeDetail.setTextColor(resources.getColor(R.color.white))
                    }
                }
            }
        })

        // Search Button click handler
        binding.btnSearchCapster.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            // hmmmmm
            checkNetworkConnection {
                val username = binding.etUsername.text.toString().trim()
                if (username.isNotEmpty()) {
                    searchEmployee(username)
                }
            }
        }

        // Next / Lihat Detail button click handler
        binding.btnSeeDetail.setOnClickListener {
            val employee = matchedEmployee ?: return@setOnClickListener
            val intent = Intent(requireContext(), AddEmployeeFormActivity::class.java).apply {
                putExtra("CURRENT_MODE", 2) // ADD Mode
                putExtra("EMPLOYEE_DATA_KEY", employee)
                putExtra("ADMIN_DATA_KEY", searchCapsterViewModel.userAdminData.value)
                putParcelableArrayListExtra("EMPLOYEE_ROLES_KEY", ArrayList(searchCapsterViewModel.employeeRoles.value ?: emptyList()))
                putParcelableArrayListExtra("EMPLOYEE_LIST_KEY", ArrayList(searchCapsterViewModel.employeeList.value ?: emptyList()))
                putParcelableArrayListExtra("OUTLET_LIST_KEY", ArrayList(searchCapsterViewModel.outletList.value ?: emptyList()))
                putExtra("IS_DIRECT_ADD", false)
            }
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
            dismiss()
            parentFragmentManager.popBackStack()
        }

        if (savedInstanceState != null) {
            matchedEmployee = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable("matched_employee", UserEmployeeData::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable("matched_employee")
            }
            searchMessage = savedInstanceState.getString("search_message", "")
            isEligible = savedInstanceState.getBoolean("is_eligible", false)
            lastSearchedUsername = savedInstanceState.getString("last_searched_username", "")

            if (searchMessage.isNotEmpty()) {
                displaySearchResult(matchedEmployee, searchMessage, isEligible)
            }
        }
    }

    private fun checkNetworkConnection(runningThisProcess: () -> Unit) {
        lifecycleScope.launch {
            if (NetworkMonitor.isOnline.value) {
                runningThisProcess()
            } else {
                val message = NetworkMonitor.errorMessage.value
                if (message.isNotEmpty()) NetworkMonitor.showToast(message, true)
            }
        }
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdSearchCapster.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdSearchCapster.width, location[1] + binding.cdSearchCapster.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdSearchCapster.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(115)
            params.bottomMargin = dpToPx(50)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdSearchCapster.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun searchEmployee(username: String) {
        lifecycleScope.launch {
            val existingEmployee = searchCapsterViewModel.employeeList.value?.find {
                it.username.equals(username, ignoreCase = false)
            }
            if (existingEmployee != null) {
                lastSearchedUsername = username
                displaySearchResult(existingEmployee, "Capster terkait sudah menjadi pegawai dari barbershop Anda!", isEligible = false)
                return@launch
            }

            try {
                val repository = EmployeeRepository(db)
                val result = repository.searchEmployeeByUsername(username, searchCapsterViewModel.employeeRoles.value ?: emptyList())
                if (result.isSuccessful) {
                    val employee = result.data
                    if (employee == null) {
                        lastSearchedUsername = username
                        displaySearchResult(null, "Tidak ditemukan data capster yang sesuai!!!")
                    } else {
                        // Verification based on eligibility rules:
                        lastSearchedUsername = username
                        if (employee.rootRef.isNotEmpty()) {
                            displaySearchResult(employee, "Capster terkait masih terafiliasi dengan barbershop lain!", isEligible = false)
                        } else if (!employee.talentAvailability) {
                            displaySearchResult(employee, "Capster sedang dalam keadaan tidak aktif mencari kerja!!!", isEligible = false)
                        } else {
                            displaySearchResult(employee, "Tekan tombol di bawah ini untuk melanjutkan!", isEligible = true)
                        }
                    }
                } else {
                    lastSearchedUsername = username
                    displaySearchResult(null, result.errorMessage ?: "Terjadi kesalahan saat mencari capster!")
                }
            } catch (e: Exception) {
                lastSearchedUsername = username
                displaySearchResult(null, e.message ?: "Terjadi kesalahan!")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displaySearchResult(employee: UserEmployeeData?, message: String, isEligible: Boolean = false) {
        this.searchMessage = message
        this.isEligible = isEligible
        with (binding) {
            tvSearchingResult.text = message
            if (isEligible) {
                tvSearchingResult.setTextColor(ContextCompat.getColor(context, R.color.grey_text_wa))
                btnSeeDetail.isEnabled = true
                btnSeeDetail.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black)
                btnSeeDetail.setTypeface(null, Typeface.BOLD)
                btnSeeDetail.setTextColor(resources.getColor(R.color.green_lime_wf))
            } else {
                tvSearchingResult.setTextColor(ContextCompat.getColor(context, R.color.magenta))
                btnSeeDetail.isEnabled = false
                btnSeeDetail.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.disable_grey_background)
                btnSeeDetail.setTypeface(null, Typeface.NORMAL)
                btnSeeDetail.setTextColor(resources.getColor(R.color.white))
            }

            if (employee != null) {
                tvEmployeeName.text = employee.fullname
                tvUsername.text = "@${employee.username}"
                tvRole.text = employee.role.ifEmpty { "    ???    " }
                val hexColor = employee.roleDetail?.hexColor
                val colorStr = if (!hexColor.isNullOrEmpty()) hexColor else "#FF8FD14F"
                try {
                    tvRole.setTextColor(colorStr.toColorInt())
                } catch (e: Exception) {
                    tvRole.setTextColor(resources.getColor(R.color.green_lime_wf))
                }

                // Set Gender
                val mappedGender = when {
                    employee.gender.equals("laki-laki", ignoreCase = true) || employee.gender.equals("pria", ignoreCase = true) || employee.gender.equals("male", ignoreCase = true) -> "Laki-laki"
                    employee.gender.equals("perempuan", ignoreCase = true) || employee.gender.equals("female", ignoreCase = true) || employee.gender.equals("wanita", ignoreCase = true) -> "Perempuan"
                    employee.gender.equals("rahasiakan", ignoreCase = true) || employee.gender.equals("unknown", ignoreCase = true) -> "Rahasiakan"
                    else -> employee.gender
                }
                setUserGender(mappedGender)

                // Set Rating stars
                val stars = listOf(ivStarOne, ivStarTwo, ivStarThree, ivStarFour, ivStarFive)
                val rating = employee.employeeRating
                for (i in stars.indices) {
                    val threshold = i.toDouble()
                    if (rating >= threshold + 1.0) {
                        stars[i].setImageResource(R.drawable.ic_star_full_figma)
                    } else if (rating >= threshold + 0.5) {
                        stars[i].setImageResource(R.drawable.ic_star_half_figma)
                    } else {
                        stars[i].setImageResource(R.drawable.ic_star_empty_figma)
                    }
                }
                tvReviewsAmount.text = getString(R.string.template_number_of_reviews, 2134)

                // Photo Profile
                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(context)
                        .load(employee.photoProfile)
                        .placeholder(R.drawable.placeholder_user_profile)
                        .error(R.drawable.placeholder_user_profile)
                        .into(ivPhotoProfile)
                } else {
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }

                matchedEmployee = employee
            } else {
                matchedEmployee = null
            }
        }
    }

    private fun setUserGender(gender: String) {
        with (binding) {
            val density = root.resources.displayMetrics.density
            val tvGenderLayoutParams = tvGender.layoutParams as ViewGroup.MarginLayoutParams
            val ivGenderLayoutParams = ivGender.layoutParams as ViewGroup.MarginLayoutParams

            when (gender) {
                "Laki-laki" -> {
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (0 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.male)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_masculine_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_male)
                    )
                    ivGenderLayoutParams.marginStart = 0
                    val paddingInDp = (0.5 * density).toInt()
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Perempuan" -> {
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.female)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_feminime_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_female)
                    )
                    ivGenderLayoutParams.marginStart = 0
                    val paddingInDp = (0.5 * density).toInt()
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Rahasiakan" -> {
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.long_text_unknown)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_unknown_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                    )
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()
                    ivGender.setPadding(0, 0, 0, 0)
                }
                else -> {
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.empty_user_gender)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_unknown_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                    )
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()
                    ivGender.setPadding(0, 0, 0, 0)
                }
            }

            tvGender.layoutParams = tvGenderLayoutParams
            ivGender.layoutParams = ivGenderLayoutParams
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(
            userAdminData: UserAdminData,
            employeeRoles: Array<EmployeeRolesData>,
            employeeList: Array<UserEmployeeData>,
            outletList: Array<Outlet>,
            isDirectAdd: Boolean
        ) = SearchUserCapsterFragment().apply {
            arguments = Bundle().apply {
                putParcelable("userAdminData", userAdminData)
                putParcelableArray("employeeRoles", employeeRoles)
                putParcelableArray("employeeList", employeeList)
                putParcelableArray("outletList", outletList)
                putBoolean("isDirectAdd", isDirectAdd)
            }
        }

        @JvmStatic
        fun newInstance() = SearchUserCapsterFragment()
    }
}
