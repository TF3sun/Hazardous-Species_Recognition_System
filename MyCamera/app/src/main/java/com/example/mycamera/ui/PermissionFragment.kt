package com.example.mycamera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import com.example.mycamera.R

class PermissionFragment : Fragment() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()){
        if(it){
            findNavController().navigate(R.id.action_permissionFragment_to_cameraFragment)
        } else {
            requireActivity().finish()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(PackageManager.PERMISSION_GRANTED !=
            requireActivity().checkSelfPermission(Manifest.permission.CAMERA)){
            request.launch(Manifest.permission.CAMERA)
        } else {
            findNavController().navigate(R.id.action_permissionFragment_to_cameraFragment)
        }
    }
}