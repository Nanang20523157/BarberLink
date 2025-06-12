package com.example.barberlink.UserInterface.Intro.OnBoarding

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.viewpager2.widget.ViewPager2
import com.example.barberlink.Adapter.OnBoardingSliderAdapter
import com.example.barberlink.DataClass.IntroDataSlide
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.databinding.ActivityOnBoardingPageBinding

class OnBoardingPage : AppCompatActivity() {
    private lateinit var binding: ActivityOnBoardingPageBinding
    private var isNavigating = false
    private var currentView: View? = null
    private val introSliderAdapter = OnBoardingSliderAdapter(
        listOf(
            IntroDataSlide(
                "Semua Kini Ada di dalam Genggaman Anda!",
                "Selamat datang di BarberLink Admin! Kami membawa Anda ke dalam era baru manajemen operasional yang terpusat dan terorganisir untuk bisnis barbershop Anda. Dapatkan kendali penuh atas bisnis Anda dengan fitur-fitur canggih dari aplikasi BarberLink Admin.",
                R.drawable.business_plan_anayitic
            ), IntroDataSlide(
                "Optimalkan Pengalaman dari Pelanggan Anda!",
                "Dapatkan wawasan yang berharga tentang pola dan preferensi kunjungan mereka untuk meningkatkan layanan dan membangun hubungan yang lebih kuat dengan para pelanggan. Jadikan setiap kunjungan mereka menjadi pengalaman yang berharga dan tak terlupakan!",
                R.drawable.customer_review_survey
            ), IntroDataSlide(
                "Lengkap Fiturnya serta Canggih Aplikasinya!",
                "Dengan aplikasi BarberLink Admin, aktivitas seperti mengelola catatan keuangan, membuat dan mengatur reservasi antrian, melakukan penjualan produk, mengevaluasi kinerja karyawan, serta memanage stok barang akan menjadi jauh lebih mudah dan lebih efisien!",
                R.drawable.performance_overview_analytic
            )
        )
    )

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityOnBoardingPageBinding.inflate(layoutInflater)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)

        // Set the adapter to the ViewPager2
        with(binding){
            introSliderPager.adapter = introSliderAdapter

            // Fungsi menampilkan Indikator
            setupIndicator()

            // Fungsi menampilkan indikator saat berpindah slide
            setIndikatorSaarIni(0)
            introSliderPager.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    setIndikatorSaarIni(position)
                }
            })

            // Atur Fungsi Tombol
            btnNext.setOnClickListener {
                if (introSliderPager.currentItem + 1 < introSliderAdapter.itemCount) {
                    introSliderPager.currentItem += 1
                } else {
                    // Buat Perpindahan Halaman saat List sudah Penuh
                    navigateToLandingPage(it)
                }
            }

            // Atur Fungsi Text OnClick Skip Intro
            textSkipIntro.setOnClickListener {
                // Perpindahan halaman
                navigateToLandingPage(it)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
    }

    private fun navigateToLandingPage(view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            Intent(this@OnBoardingPage, LandingPage::class.java).also {
                startActivity(it)
            }
            finish()
        } else return
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    private fun setupIndicator() {
        val indikator = arrayOfNulls<ImageView>(introSliderAdapter.itemCount)
        val layoutParams: LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        layoutParams.setMargins(8,0,8,0)
        for (i in indikator.indices){
            indikator[i] = ImageView(applicationContext)
            indikator[i].apply {
                this?.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.intro_indicator_inactive
                    )
                )
                this?.layoutParams = layoutParams
            }

            // Konfigurasi Linear Layout
            binding.indicatorsContainer.addView(indikator[i])
        }
    }

    // Fungsi Merubah Indikator saat berpindah Halaman
    private fun setIndikatorSaarIni(index: Int) {
        with(binding){
            val childCount =  indicatorsContainer.childCount
            for (i in 0 until childCount) {
                val imageView = indicatorsContainer[i] as ImageView
                if (i == index){
                    imageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.intro_indicator_active
                        )
                    )
                } else{
                    imageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.intro_indicator_inactive
                        )
                    )
                }
            }
        }
    }


}