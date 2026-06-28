package com.example.barberlink.UserInterface.Admin.Fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListBundlingChangeAdapter
import com.example.barberlink.Adapter.ItemListQueueResetAdapter
import com.example.barberlink.DataClass.BundlingChangeInfo
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ManageBundlingPage
import com.example.barberlink.UserInterface.Admin.ManageEmployeePage
import com.example.barberlink.UserInterface.Admin.ManageOutletPage
import com.example.barberlink.UserInterface.Admin.ManageProductPage
import com.example.barberlink.UserInterface.Admin.ManageServicePage
import com.example.barberlink.UserInterface.Admin.ViewModel.ConfirmDeleteViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageBundlingViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageEmployeeViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageOutletViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageProductViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageServiceViewModel
import androidx.core.text.HtmlCompat
import com.example.barberlink.databinding.FragmentConfirmDeleteItemBinding
import com.example.barberlink.databinding.FragmentResetQueueBoardBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.getValue

// NTODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ConfirmDeleteItemFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConfirmDeleteItemFragment : DialogFragment() {
    // NTODO: Rename and change types of parameters
    private var _binding: FragmentConfirmDeleteItemBinding? = null

    private val parentFragmentViewModel: ConfirmDeleteViewModel by lazy {
        when (requireActivity()) {
            is ManageOutletPage -> activityViewModels<ManageOutletViewModel>().value
            is ManageBundlingPage -> activityViewModels<ManageBundlingViewModel>().value
            is ManageProductPage -> activityViewModels<ManageProductViewModel>().value
            is ManageEmployeePage -> activityViewModels<ManageEmployeeViewModel>().value
            is ManageServicePage -> activityViewModels<ManageServiceViewModel>().value
            else -> throw IllegalStateException("Fragment ini gagal dibuka di ${requireActivity().javaClass.simpleName}")
        }
    }
    private val debounce by lazy { ScopedUniversalDebounce() }
    private lateinit var context: Context
    private lateinit var bundleAdapter: ItemListBundlingChangeAdapter
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private var isConfirmDelete: Boolean = false
    private var isFirstLoad: Boolean = true
    private var isHandled = false

    private val binding get() = _binding!!

    private var title: String? = null
    private var subtitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentViewModel
        arguments?.let {
            title = it.getString(ARG_PARAM1)
            subtitle = it.getString(ARG_PARAM2)
        }
        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isHandled = savedInstanceState.getBoolean("is_handled", false)
            isConfirmDelete = savedInstanceState.getBoolean("is_confirm_delete", false)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentConfirmDeleteItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = title
        binding.tvSubtitle.text = HtmlCompat.fromHtml(subtitle ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY)
        bundleAdapter = ItemListBundlingChangeAdapter()
        binding.rvListBundling.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.rvListBundling.adapter = bundleAdapter

        binding.rvListBundling.visibility = if (parentFragmentViewModel.bundleChangeList.value?.isEmpty() == true) View.GONE else View.VISIBLE
        if (isFirstLoad) bundleAdapter.setShimmer(true)
        else bundleAdapter.setShimmer(false)

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

                Log.d("ConfirmDeleteItemFragment", "Background scrim clicked")
//                setFragmentResult("action_dismiss_dialog", bundleOf(
//                    "dismiss_dialog" to true
//                ))
                isConfirmDelete = false

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

        parentFragmentViewModel.bundleChangeList.observe(viewLifecycleOwner) { bundleChangeList ->
            // Menggunakan coroutine untuk menunda eksekusi submitList
            lifecycleScope.launch {
                // Hitung mundur 800 ms
                if (isFirstLoad) delay(500)

                // Submit data ke adapter setelah delay
                bundleAdapter.submitList(bundleChangeList)

                // Matikan shimmer setelah data di-submit
                if (isFirstLoad) {
                    bundleAdapter.setShimmer(false)
                    isFirstLoad = false
                } else {
                    bundleAdapter.notifyDataSetChanged()
                }

                val layoutParams = binding.rvListBundling.layoutParams
                layoutParams.height = if (bundleChangeList.size > 3) {
                    resources.getDimensionPixelSize(R.dimen.recycler_height_large_bundle_change) // 315dp dalam pixels
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }
                binding.rvListBundling.layoutParams = layoutParams
            }
        }

        binding.btnDelete.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            // hmmmmm
            checkNetworkConnection {
//                setFragmentResult("action_delete_user", bundleOf(
//                    "confirm_delete" to true,
//                    "dismiss_dialog" to true
//                ))
                isConfirmDelete = true

                dismiss()
                parentFragmentManager.popBackStack()
            }
        }

        binding.btnCancel.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            // hmmmmm
//            setFragmentResult("action_dismiss_dialog", bundleOf(
//                "dismiss_dialog" to true
//            ))
            isConfirmDelete = false

            dismiss()
            parentFragmentManager.popBackStack()
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_handled", isHandled)
        outState.putBoolean("is_confirm_delete", isConfirmDelete)
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

    private fun handleDismissAction() {
        if (isHandled) {
            Log.d("TagDismiss", "onDismiss: Already handled")
            return
        }

        setFragmentResult("action_delete_user", bundleOf(
            "confirm_delete" to isConfirmDelete,
            "dismiss_dialog" to true
        ))
        isHandled = true
        Log.d("TagDismiss", "onDismiss: 94")
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdConfirmDelete.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdConfirmDelete.width, location[1] + binding.cdConfirmDelete.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdConfirmDelete.layoutParams as ViewGroup.MarginLayoutParams
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

        binding.cdConfirmDelete.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::bundleAdapter.isInitialized) bundleAdapter.stopAllShimmerEffects()
        _binding = null

        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        handleDismissAction()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ConfirmDeleteItemFragment.
         */
        // NTODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(title: String, subtitle: String) =
            ConfirmDeleteItemFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, title)
                    putString(ARG_PARAM2, subtitle)
                }
            }

    }
}