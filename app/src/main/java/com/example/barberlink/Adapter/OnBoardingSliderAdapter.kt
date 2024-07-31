package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.IntroDataSlide
import com.example.barberlink.databinding.OnBoardingSliderAdapterBinding

class OnBoardingSliderAdapter(private val introDataSlide: List<IntroDataSlide>):
    RecyclerView.Adapter<OnBoardingSliderAdapter.OnBoardingSlideViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnBoardingSlideViewHolder {
        val binding = OnBoardingSliderAdapterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OnBoardingSlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnBoardingSlideViewHolder, position: Int) {
        holder.bind(introDataSlide[position])
    }

    override fun getItemCount(): Int {
        return introDataSlide.size
    }

    inner class OnBoardingSlideViewHolder(private val binding: OnBoardingSliderAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(introDataSlide: IntroDataSlide) {
            with(binding) {
                textTitle.text = introDataSlide.title
                textDescription.text = introDataSlide.description
                imageSlideIcon.setImageResource(introDataSlide.image)
            }
        }
    }
}