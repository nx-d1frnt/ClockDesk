package com.nxd1frnt.clockdesk2.connect.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice

class DeskConnectSettingsFragment : Fragment() {

    private lateinit var deskConnectManager: DeskConnectManager
    private lateinit var switchDeskConnect: MaterialSwitch
    private lateinit var txtDeviceName: TextView
    private lateinit var txtFingerprint: TextView
    private lateinit var txtNoPaired: TextView
    private lateinit var txtDiscovering: TextView
    private lateinit var layoutPaired: LinearLayout
    private lateinit var layoutAvailable: LinearLayout

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {
            activity?.runOnUiThread { refreshDeviceLists() }
        }

        override fun onDeviceConnected(device: DeskConnectDevice) {
            activity?.runOnUiThread { refreshDeviceLists() }
        }

        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            activity?.runOnUiThread { refreshDeviceLists() }
        }

        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {
            activity?.let { act ->
                act.runOnUiThread {
                    showPairingDialog(act, device, verificationKey)
                }
            }
        }

        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            activity?.runOnUiThread { refreshDeviceLists() }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_desk_connect_settings, container, false)
        deskConnectManager = DeskConnectManager.getInstance(requireContext())

        switchDeskConnect = view.findViewById(R.id.switch_deskconnect)
        txtDeviceName = view.findViewById(R.id.txt_device_name)
        txtFingerprint = view.findViewById(R.id.txt_fingerprint)
        txtNoPaired = view.findViewById(R.id.txt_no_paired)
        txtDiscovering = view.findViewById(R.id.txt_discovering)
        layoutPaired = view.findViewById(R.id.layout_paired_devices)
        layoutAvailable = view.findViewById(R.id.layout_available_devices)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        switchDeskConnect.isChecked = deskConnectManager.isEnabled
        switchDeskConnect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("deskconnect_enabled", isChecked).apply()
            if (isChecked) {
                deskConnectManager.start()
            } else {
                deskConnectManager.stop()
            }
            refreshDeviceLists()
        }

        txtDeviceName.text = deskConnectManager.customDeviceName
        val fp = deskConnectManager.security.getCertificateFingerprint()
        txtFingerprint.text = "Fingerprint: $fp"

        return view
    }

    override fun onResume() {
        super.onResume()
        deskConnectManager.deviceListeners.add(deviceListener)
        deskConnectManager.broadcastDiscovery()
        refreshDeviceLists()
    }

    override fun onPause() {
        super.onPause()
        deskConnectManager.deviceListeners.remove(deviceListener)
    }

    private fun refreshDeviceLists() {
        if (!isAdded) return

        layoutPaired.removeAllViews()
        layoutAvailable.removeAllViews()

        val allDevices = deskConnectManager.discoveredDevices.values.toList()
        val pairedDevices = allDevices.filter { it.isPaired }
        val availableDevices = allDevices.filter { !it.isPaired }

        txtNoPaired.visibility = if (pairedDevices.isEmpty()) View.VISIBLE else View.GONE
        txtDiscovering.visibility = if (availableDevices.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())

        for (dev in pairedDevices) {
            val itemView = inflater.inflate(R.layout.item_connected_device, layoutPaired, false)
            val nameView = itemView.findViewById<TextView>(R.id.device_name)
            val statusView = itemView.findViewById<TextView>(R.id.device_status)
            val btnAction = itemView.findViewById<MaterialButton>(R.id.btn_action)

            nameView.text = dev.getDisplayName()
            val statusText = if (dev.isConnected) "Connected" else "Paired (Offline)"
            statusView.text = if (dev.ipAddress != null) "$statusText • ${dev.ipAddress?.hostAddress}" else statusText

            itemView.setOnClickListener {
                if (dev.isConnected) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(dev.getDisplayName())
                        .setItems(arrayOf("Send Ping", "Unpair")) { _, which ->
                            when (which) {
                                0 -> {
                                    deskConnectManager.sendPing(dev.deviceId)
                                    android.widget.Toast.makeText(context, "Ping sent to ${dev.getDisplayName()}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                1 -> {
                                    deskConnectManager.unpairDevice(dev.deviceId)
                                    refreshDeviceLists()
                                }
                            }
                        }
                        .show()
                }
            }

            btnAction.text = getString(R.string.deskconnect_unpair)
            btnAction.setOnClickListener {
                deskConnectManager.unpairDevice(dev.deviceId)
                refreshDeviceLists()
            }
            layoutPaired.addView(itemView)
        }

        for (dev in availableDevices) {
            val itemView = inflater.inflate(R.layout.item_connected_device, layoutAvailable, false)
            val nameView = itemView.findViewById<TextView>(R.id.device_name)
            val statusView = itemView.findViewById<TextView>(R.id.device_status)
            val btnAction = itemView.findViewById<MaterialButton>(R.id.btn_action)

            nameView.text = dev.getDisplayName()
            statusView.text = dev.ipAddress?.hostAddress ?: "Available"

            btnAction.text = getString(R.string.deskconnect_pair)
            btnAction.setOnClickListener {
                deskConnectManager.requestPair(dev)
                btnAction.isEnabled = false
                btnAction.text = "Pairing…"
            }
            layoutAvailable.addView(itemView)
        }
    }

    private fun showPairingDialog(context: Context, device: DeskConnectDevice, verificationKey: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.deskconnect_pairing_request_title)
            .setMessage(getString(R.string.deskconnect_pairing_request_message, device.getDisplayName(), verificationKey))
            .setPositiveButton(R.string.deskconnect_accept) { _, _ ->
                deskConnectManager.acceptPair(device)
            }
            .setNegativeButton(R.string.deskconnect_reject) { _, _ ->
                deskConnectManager.rejectPair(device)
            }
            .setCancelable(false)
            .show()
    }
}
