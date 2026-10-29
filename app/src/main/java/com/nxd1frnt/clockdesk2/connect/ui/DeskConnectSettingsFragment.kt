package com.nxd1frnt.clockdesk2.connect.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class DeskConnectSettingsFragment : BaseM3SettingsFragment() {
    private lateinit var deskConnectManager: DeskConnectManager
    private val pendingPairIds = mutableSetOf<String>()

    private val batteryListener = object : DeskConnectManager.BatteryListener {
        override fun onBatteryUpdated(deviceId: String, currentCharge: Int, isCharging: Boolean, thresholdEvent: Int) {
            refreshDeviceLists()
        }
    }

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) = refreshDeviceLists()
        override fun onDeviceConnected(device: DeskConnectDevice) = refreshDeviceLists()
        override fun onDeviceDisconnected(device: DeskConnectDevice) = refreshDeviceLists()

        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {
            activity?.runOnUiThread {
                if (view != null) showPairingDialog(requireContext(), device, verificationKey)
            }
        }

        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            activity?.runOnUiThread {
                pendingPairIds.remove(device.deviceId)
                refreshDeviceLists()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DeskConnect stores its identity and preferences in the default preference file.
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        deskConnectManager = DeskConnectManager.getInstance(requireContext())
    }

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.deskconnect_settings_title)) {
            switch(
                key = "deskconnect_enabled",
                title = getString(R.string.deskconnect_enable_title),
                summary = getString(R.string.deskconnect_enable_summary),
                iconRes = R.drawable.ic_devices,
                defaultValue = true,
                onCheckedChange = { enabled ->
                    deskConnectManager.isEnabled = enabled
                    pendingPairIds.clear()
                }
            )
            action(
                title = getString(R.string.deskconnect_device_name_title),
                summary = deskConnectManager.customDeviceName
            ) { }
            action(
                title = getString(R.string.deskconnect_fingerprint),
                summary = deskConnectManager.security.getCertificateFingerprint()
            ) { }
        }

        val devices = deskConnectManager.discoveredDevices.values.toList()
            .sortedBy { it.getDisplayName().lowercase() }
        category(getString(R.string.deskconnect_paired_devices_title)) {
            val paired = devices.filter { it.isPaired }
            if (paired.isEmpty()) {
                action(title = getString(R.string.deskconnect_no_paired_devices), isEnabled = { false }) { }
            }
            paired.forEach { device ->
                action(
                    key = "paired_${device.deviceId}",
                    title = device.getDisplayName(),
                    summary = deviceSummary(device),
                    iconRes = device.getDeviceTypeIconRes(),
                    statusText = if (device.isConnected && device.batteryLevel >= 0) "${device.batteryLevel}%" else null,
                    showChevron = true
                ) { showDeviceActions(device) }
            }
        }
        category(getString(R.string.deskconnect_discovered_devices_title)) {
            action(
                title = getString(R.string.deskconnect_refresh),
                iconRes = R.drawable.ic_refresh,
                isEnabled = { deskConnectManager.isEnabled }
            ) { performRefresh() }
            val available = devices.filter { !it.isPaired }
            if (available.isEmpty() || !deskConnectManager.isEnabled) {
                action(
                    title = getString(if (deskConnectManager.isEnabled) R.string.deskconnect_no_devices_found
                        else R.string.deskconnect_enable_title),
                    isEnabled = { false }
                ) { }
            }
            if (deskConnectManager.isEnabled) available.forEach { device ->
                action(
                    key = "available_${device.deviceId}",
                    title = device.getDisplayName(),
                    summary = deviceSummary(device),
                    iconRes = device.getDeviceTypeIconRes(),
                    statusText = getString(if (device.deviceId in pendingPairIds) R.string.deskconnect_pairing
                        else R.string.deskconnect_pair),
                    isEnabled = { device.deviceId !in pendingPairIds }
                ) {
                    pendingPairIds.add(device.deviceId)
                    deskConnectManager.requestPair(device)
                    refreshDeviceLists()
                    view?.postDelayed({
                        pendingPairIds.remove(device.deviceId)
                        refreshDeviceLists()
                    }, 30_000L)
                }
            }
        }
        category(getString(R.string.about_credits_title)) {
            action(
                title = getString(R.string.about_kde_connect_title),
                summary = getString(R.string.deskconnect_credits_text),
                iconRes = R.drawable.ic_kde_24dp,
                showChevron = true
            ) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_kde_connect_url))))
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_deskconnect_settings, menu)
                val palette = UiMonetThemeManager.getPalette(requireContext())
                for (i in 0 until menu.size()) {
                    menu.getItem(i)?.icon?.mutate()?.setTint(palette.onSurface)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        performRefresh()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun performRefresh() {
        if (!isAdded) return
        if (!deskConnectManager.isEnabled) {
            Toast.makeText(context, R.string.deskconnect_enable_title, Toast.LENGTH_SHORT).show()
            return
        }
        activity?.findViewById<View>(R.id.action_refresh)?.animate()?.rotationBy(360f)?.setDuration(600L)?.start()
        Toast.makeText(context, R.string.deskconnect_refreshing, Toast.LENGTH_SHORT).show()
        deskConnectManager.refreshDiscovery()
        deskConnectManager.requestAllBatteries()
        refreshDeviceLists()
    }

    override fun onResume() {
        super.onResume()
        deskConnectManager.deviceListeners.add(deviceListener)
        deskConnectManager.batteryListeners.add(batteryListener)
        if (deskConnectManager.isEnabled) {
            deskConnectManager.broadcastDiscovery()
            deskConnectManager.requestAllBatteries()
        }
        refreshDeviceLists()
    }

    override fun onPause() {
        deskConnectManager.deviceListeners.remove(deviceListener)
        deskConnectManager.batteryListeners.remove(batteryListener)
        super.onPause()
    }

    override fun onDestroyView() {
        pendingPairIds.clear()
        super.onDestroyView()
    }

    private fun refreshDeviceLists() {
        activity?.runOnUiThread {
            if (isAdded && view != null) rebuildSettings()
        }
    }

    private fun deviceSummary(device: DeskConnectDevice): String = buildList {
        add(device.getDeviceTypeDisplayName())
        if (device.isPaired) {
            add(getString(if (device.isConnected) R.string.deskconnect_status_connected
                else R.string.deskconnect_status_paired_offline))
            if (device.isConnected && device.isCharging) add(getString(R.string.deskconnect_battery_charging))
        }
        device.ipAddress?.hostAddress?.let { add(it) }
    }.joinToString(" • ")

    private fun showDeviceActions(device: DeskConnectDevice) {
        val canPing = device.isConnected
        val actions = mutableListOf(getString(R.string.deskconnect_unpair))
        if (canPing) actions.add(0, getString(R.string.deskconnect_send_ping))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(device.getDisplayName())
            .setItems(actions.toTypedArray()) { _, index ->
                if (canPing && index == 0) {
                    deskConnectManager.sendPing(device.deviceId)
                    Toast.makeText(context, getString(R.string.deskconnect_ping_sent, device.getDisplayName()), Toast.LENGTH_SHORT).show()
                } else {
                    deskConnectManager.unpairDevice(device.deviceId)
                    refreshDeviceLists()
                }
            }
            .show()
        UiMonetThemeManager.styleAlertDialog(dialog)
    }

    private fun showPairingDialog(context: Context, device: DeskConnectDevice, verificationKey: String) {
        val dialog = MaterialAlertDialogBuilder(context)
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
        UiMonetThemeManager.styleAlertDialog(dialog)
    }
}
