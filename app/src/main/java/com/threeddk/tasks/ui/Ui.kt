package com.threeddk.tasks.ui
import android.view.*
import android.graphics.drawable.GradientDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.bumptech.glide.Glide
import com.threeddk.tasks.R
import com.threeddk.tasks.databinding.ItemRowBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
fun <T> Fragment.watch(flow:Flow<T>,block:(T)->Unit)=viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){flow.collect{block(it)}}}
