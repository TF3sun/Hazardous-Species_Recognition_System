package com.example.mycamera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.mycamera.R
import com.example.mycamera.databinding.FragmentCameraBinding
import com.example.mycamera.detector.OnDetectedListener
import com.example.mycamera.detector.PytorchDetector
import com.example.mycamera.detector.Result
import com.github.kittinunf.fuel.httpPost
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.gson.JsonObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.github.kittinunf.result.Result as KittinResult


class CameraFragment : Fragment() {

    private var _binding:FragmentCameraBinding? = null
    private val binding get() =  _binding!!

    private val model by viewModels<CameraModel>()

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var detector: PytorchDetector

    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null // 현재 위치를 가져오기 위한 변수
    lateinit var mLastLocation: Location // 위치 값을 가지고 있는 객체
    internal lateinit var mLocationRequest: LocationRequest // 위치 정보 요청의 매개변수를 저장하는
    private val REQUEST_PERMISSION_LOCATION = 10

    lateinit var button: Button
    lateinit var text1: TextView

    private val detectionListener = object: OnDetectedListener {
        override fun onError(err: String) {
            Log.e(TAG, err)
        }

        override fun onResults(results: List<Result>) {
            model.results.postValue(results)
        }

        override fun onModelLoaded(names:Map<Int, String>) {
            binding.resultView.setNames(names)
            Toast.makeText(requireContext(), "Data Loaded", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        viewFinder = view.findViewById(R.id.viewFinder)

        model.results.observe(viewLifecycleOwner){result -> onResult(result) }

        detector = PytorchDetector(requireContext(), MODEL_PATH, LABEL_PATH, detectionListener)

        cameraExecutor = Executors.newSingleThreadExecutor()
        if(PackageManager.PERMISSION_GRANTED ==
            requireActivity().checkSelfPermission(Manifest.permission.CAMERA)){
            startCamera()
        }

        button = view.findViewById(R.id.button)
        text1 = view.findViewById(R.id.text1)

        button.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSION_LOCATION
                )
                return@setOnClickListener
            }
            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
            mFusedLocationProviderClient?.lastLocation?.addOnSuccessListener { location: Location? ->

                if (location != null) {
                    mLastLocation = location
                    //text1.text = "위도: ${mLastLocation.latitude}, 경도: ${mLastLocation.longitude}"
                    SendToServerLocationData(mLastLocation)
                }
            }
        }
    }

    private fun onResult(results: List<Result>){
        binding.chipGroup.removeAllViews()
        results.forEach {
            if(it.classIndex in 0 until detector.names.size) {
                val msg = "${detector.names[it.classIndex]} : ${it.score}"
                val detect_name = detector.names[it.classIndex]
                if(detect_name == "Sicyos angulatus"){
                    val detect_name = "가시박"
                }else if(detect_name == "Humulus japonicus Siebold"){
                    val detect_name_kr = "환삼덩굴"
                }else if(detect_name == "Prickly lettuce"){
                    val detect_name_kr = "가시상추"
                }

                text1.text = "해당 식물은 유해종인 ${detect_name}입니다.\n위의 버튼을 눌러 해당 식물이 찍힌 위치 정보를 공유해 주시면 해당 데이터를 수집하여 환경부 외래종 관리 시스템으로 전달됩니다."
                val chip = Chip(requireContext()).apply{
                    text = msg
                }
                binding.chipGroup.addView(chip)
            }
        }
        binding.resultView.updateResults(results)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }

    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = getPreviewUseCase()
            val imageAnalysis = getAnalysisUseCase()

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalysis)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // Camera Preview
    private fun getPreviewUseCase(): Preview {
        return Preview.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
            .apply { setSurfaceProvider(binding.viewFinder.surfaceProvider) }
    }

    // Image Analysis
    private fun getAnalysisUseCase(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .apply {
                setAnalyzer( cameraExecutor ) { image ->
                    detector.detect(image, binding.resultView.width, binding.resultView.height)
                }
            }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun SendToServerLocationData(mLastLocation: Location) {
        // 데이터 서버 주소
        val url = "http://192.168.0.34:5000/save_json"

        val text = text1.text.toString()
//        showToast(text)
        //val regex = Regex("해당 식물은 유해종인 (.*)입니다.")
        val regex = Regex("\\((.*?)\\)")

        val matchResult = regex.find(text)
        val name = matchResult?.groupValues?.get(1)

        // 보낼 JSON 데이터
        // 위도 경도, 위치 정확도
        val jsonData = JsonObject().apply {
            addProperty("name", name)
            addProperty("latitude", mLastLocation.latitude)
            addProperty("longitude", mLastLocation.longitude)
            addProperty("accuracy", mLastLocation.accuracy)
        }

        // POST 요청 보내기
        url.httpPost()
            .header("Content-Type" to "application/json")
            .body(jsonData.toString())
            .response { _, response, result ->
                when (result) {
                    is KittinResult.Success -> {
                        val data = result.get()
                        showToast("Data sent successfully. Response status code: ${response.statusCode}")
                    }

                    is KittinResult.Failure -> {
                        val ex = result.getException()
                        showToast("Error: $ex")
                    }
                }
            }
    }

    companion object{
        private const val TAG = "Camera"
        private const val LABEL_PATH = "classes.yaml" // "classes_yaml"
        private const val MODEL_PATH = "plant_e100.torchscript.ptl"//"yolov5s.torchscript.ptl"
    }
}