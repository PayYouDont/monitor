package com.payudon.monitor.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.payudon.monitor.R
import com.payudon.monitor.databinding.FragmentHomeBinding
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.rtmp.RtmpCamera1


class HomeFragment : Fragment(), ConnectCheckerRtmp, SurfaceHolder.Callback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val TAG = "HomeFragment"
    private lateinit var rtmpCamera1: RtmpCamera1
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        rtmpCamera1 = RtmpCamera1(binding.surfaceViewTop, this)
        rtmpCamera1.setReTries(10)
        binding.surfaceViewTop.holder.addCallback(this)
        binding.switchCamera.setOnClickListener {
            try {
                rtmpCamera1.switchCamera()
            } catch (e: CameraOpenException) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
        binding.bStartStop.setOnClickListener {
            if (!rtmpCamera1.isStreaming) {
                if (rtmpCamera1.isRecording || rtmpCamera1.prepareVideo(
                        640,
                        480,
                        30,
                        1200 * 1024,
                        90
                    ) && rtmpCamera1.prepareAudio()
                ) {
                    binding.bStartStop.setText(R.string.stop_button)
                    rtmpCamera1.startStream(binding.urlPush.text.toString())
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Error preparing stream, This device cant do it",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                binding.bStartStop.setText(R.string.start_button)
                rtmpCamera1.stopStream()
            }
        }
        return binding.root
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        if (rtmpCamera1.isStreaming) {
            rtmpCamera1.stopStream()
            binding.bStartStop.setText(resources.getString(R.string.start_button))
        }
        rtmpCamera1.stopPreview()
    }

    override fun onAuthErrorRtmp() {
    }

    override fun onAuthSuccessRtmp() {

    }

    override fun onConnectionFailedRtmp(reason: String) {
        requireActivity().runOnUiThread {
            if (rtmpCamera1.reTry(5000, reason)) {
                Toast.makeText(requireContext(), "Retry", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG,"Connection failed. $reason")
                Toast.makeText(requireContext(), "Connection failed. $reason", Toast.LENGTH_SHORT).show()
                rtmpCamera1.stopStream()
                binding.bStartStop.setText(R.string.start_button)
            }
        }
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "onConnectionStartedRtmp", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionSuccessRtmp() {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Connection success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisconnectRtmp() {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "onNewBitrateRtmp", Toast.LENGTH_SHORT).show()
        }
    }
}