package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.ui.settings.SettingsActivity
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class DevicesDeskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val deskConnectManager = DeskConnectManager.getInstance(context)
    private val recyclerDevices: RecyclerView
    private val layoutEmpty: LinearLayout
    private val btnScan: MaterialButton
    private val btnSettings: MaterialButton
    private val btnOpenSettings: MaterialButton
    private val deviceAdapter: DevicesAdapter

    private val deviceList = mutableListOf<DeskConnectDevice>()

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {
            post { refreshDevices() }
        }

        override fun onDeviceConnected(device: DeskConnectDevice) {
            post { refreshDevices() }
        }

        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            post { refreshDevices() }
        }

        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {
            post { refreshDevices() }
        }

        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            post { refreshDevices() }
        }
    }

    private val batteryListener = object : DeskConnectManager.BatteryListener {
        override fun onBatteryUpdated(deviceId: String, currentCharge: Int, isCharging: Boolean, thresholdEvent: Int) {
            post { refreshDevices() }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_devices_layout, this, true)

        recyclerDevices = findViewById(R.id.recycler_devices)
        layoutEmpty = findViewById(R.id.layout_empty_devices)
        btnScan = findViewById(R.id.btn_scan_devices)
        btnSettings = findViewById(R.id.btn_devices_settings)
        btnOpenSettings = findViewById(R.id.btn_open_deskconnect_settings)

        recyclerDevices.layoutManager = LinearLayoutManager(context)
        deviceAdapter = DevicesAdapter(
            items = deviceList,
            onPing = { device ->
                deskConnectManager.sendPing(device.deviceId)
                Toast.makeText(context, context.getString(R.string.device_ping_sent, device.getDisplayName()), Toast.LENGTH_SHORT).show()
            }
        )
        recyclerDevices.adapter = deviceAdapter

        btnScan.setOnClickListener {
            deskConnectManager.refreshDiscovery()
            deskConnectManager.broadcastDiscovery()
            deskConnectManager.requestAllBatteries()
            Toast.makeText(context, R.string.device_refresh_scan, Toast.LENGTH_SHORT).show()
            refreshDevices()
        }

        val openSettingsAction = OnClickListener {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                putExtra("category_id", "deskconnect")
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        btnSettings.setOnClickListener(openSettingsAction)
        btnOpenSettings.setOnClickListener(openSettingsAction)
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        deviceAdapter.notifyDataSetChanged()
        DeskForegroundStyle.apply(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMonetTheme()
        deskConnectManager.deviceListeners.add(deviceListener)
        deskConnectManager.batteryListeners.add(batteryListener)
        deskConnectManager.broadcastDiscovery()
        deskConnectManager.requestAllBatteries()
        refreshDevices()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        deskConnectManager.deviceListeners.remove(deviceListener)
        deskConnectManager.batteryListeners.remove(batteryListener)
    }

    fun refreshDevices() {
        val pairedDevices = deskConnectManager.discoveredDevices.values
            .filter { it.isPaired || deskConnectManager.security.isDevicePaired(it.deviceId) }
            .sortedWith(compareByDescending<DeskConnectDevice> { it.isConnected }.thenBy { it.getDisplayName().lowercase() })

        deviceList.clear()
        deviceList.addAll(pairedDevices)
        deviceAdapter.notifyDataSetChanged()

        if (deviceList.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            recyclerDevices.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            recyclerDevices.visibility = View.VISIBLE
        }
    }

    private class DevicesAdapter(
        private val items: List<DeskConnectDevice>,
        private val onPing: (DeskConnectDevice) -> Unit
    ) : RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgIcon: ImageView = view.findViewById(R.id.img_device_icon)
            val txtName: TextView = view.findViewById(R.id.txt_device_name)
            val txtStatus: TextView = view.findViewById(R.id.txt_device_status)
            val dotStatus: View = view.findViewById(R.id.view_status_dot)
            val layoutBattery: LinearLayout = view.findViewById(R.id.layout_battery_info)
            val imgBattery: ImageView = view.findViewById(R.id.img_device_battery)
            val txtBattery: TextView = view.findViewById(R.id.txt_device_battery)
            val btnPing: MaterialButton = view.findViewById(R.id.btn_ping_device)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desk_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = items[position]
            val context = holder.itemView.context
            val palette = UiMonetThemeManager.getPalette(context)

            (holder.itemView as? MaterialCardView)?.let { card ->
                card.setCardBackgroundColor(palette.surfaceContainer)
                card.strokeColor = palette.outlineVariant
                card.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 32))
            }

            holder.txtName.text = device.getDisplayName()
            holder.txtName.setTextColor(palette.onSurface)
            holder.imgIcon.setImageResource(device.getDeviceTypeIconRes())
            holder.imgIcon.imageTintList = ColorStateList.valueOf(palette.primary)
            (holder.imgIcon.background?.mutate() as? GradientDrawable)?.setColor(palette.surfaceContainerHigh)

            // Status online/offline
            val isOnline = device.isConnected
            holder.txtStatus.text = if (isOnline) {
                context.getString(R.string.device_status_online)
            } else {
                context.getString(R.string.device_status_offline)
            }
            holder.txtStatus.setTextColor(palette.onSurfaceVariant)
            (holder.dotStatus.background?.mutate() as? GradientDrawable)?.setColor(
                if (isOnline) palette.primary else palette.outlineVariant
            )
            holder.dotStatus.alpha = if (isOnline) 1.0f else 0.4f

            // Battery
            val batteryLevel = device.batteryLevel
            if (batteryLevel != null && batteryLevel >= 0) {
                holder.layoutBattery.visibility = View.VISIBLE
                holder.txtBattery.text = context.getString(R.string.device_battery_level, batteryLevel)
                holder.txtBattery.setTextColor(palette.onSurface)
                holder.imgBattery.imageTintList = ColorStateList.valueOf(palette.onSurface)
                (holder.layoutBattery.background?.mutate() as? GradientDrawable)?.setColor(palette.surfaceContainerHigh)
                if (device.isCharging == true) {
                    holder.imgBattery.setImageResource(R.drawable.ic_battery_charging)
                } else if (batteryLevel <= 20) {
                    holder.imgBattery.setImageResource(R.drawable.ic_battery_alert)
                } else {
                    holder.imgBattery.setImageResource(R.drawable.ic_battery_full)
                }
            } else {
                holder.layoutBattery.visibility = View.GONE
            }

            // Ping button
            holder.btnPing.visibility = if (isOnline) View.VISIBLE else View.GONE
            UiMonetThemeManager.styleMaterialButton(holder.btnPing, palette)
            holder.btnPing.setOnClickListener {
                onPing(device)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
