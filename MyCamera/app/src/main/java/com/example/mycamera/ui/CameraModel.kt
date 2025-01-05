package com.example.mycamera.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.mycamera.detector.Result

class CameraModel: ViewModel() {
    val results = MutableLiveData<List<Result>>()
}