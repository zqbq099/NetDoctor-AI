package com.netdoctor.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvNetworkType: TextView
    private lateinit var tvSignalStrength: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvOperator: TextView
    private lateinit var btnDiagnose: Button
    private lateinit var btnApnGuide: Button
    private lateinit var btnDeepSeek: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        checkPermissions()
    }

    private fun initViews() {
        tvNetworkType = findViewById(R.id.tv_network_type)
        tvSignalStrength = findViewById(R.id.tv_signal_strength)
        tvIpAddress = findViewById(R.id.tv_ip_address)
        tvOperator = findViewById(R.id.tv_operator)
        btnDiagnose = findViewById(R.id.btn_diagnose)
        btnApnGuide = findViewById(R.id.btn_apn_guide)
        btnDeepSeek = findViewById(R.id.btn_deepseek)
    }

    private fun setupClickListeners() {
        btnDiagnose.setOnClickListener { diagnoseNetwork() }
        btnApnGuide.setOnClickListener { showApnGuide() }
        btnDeepSeek.setOnClickListener {
            startActivity(Intent(this, DeepSeekChatActivity::class.java))
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 100)
        }
    }

    private fun diagnoseNetwork() {
        val info = getNetworkInfo()
        tvNetworkType.text = "🌐 نوع الشبكة: ${info["type"]}"
        tvSignalStrength.text = "📶 قوة الإشارة: ${info["signal"]}%"
        tvIpAddress.text = "📍 عنوان IP: ${info["ip"]}"
        tvOperator.text = "📱 المشغل: ${info["operator"]}"
        Toast.makeText(this, "تم تشخيص الشبكة", Toast.LENGTH_SHORT).show()
    }

    private fun getNetworkInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            info["type"] = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi 📶"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "4G/5G 📱"
                else -> "غير متصل"
            }
            info["signal"] = "75"
            info["ip"] = getLocalIpAddress()
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                info["operator"] = tm.networkOperatorName ?: "غير معروف"
            } else {
                info["operator"] = "غير معروف"
            }
        } catch (e: Exception) {
            info["type"] = "خطأ: ${e.message}"
            info["signal"] = "0"
            info["ip"] = "غير متوفر"
            info["operator"] = "غير معروف"
        }
        return info
    }

    private fun getLocalIpAddress(): String {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') != true }
                ?.hostAddress ?: "غير متوفر"
        } catch (e: Exception) {
            "غير متوفر"
        }
    }

    private fun showApnGuide() {
        val countries = arrayOf("🇸🇦 السعودية", "🇪🇬 مصر", "🇦🇪 الإمارات", "🇰🇼 الكويت", "🇶🇦 قطر")
        AlertDialog.Builder(this).setTitle("📖 دليل إعدادات APN").setItems(countries) { _, which ->
            when (which) {
                0 -> showApnDetails("STC", "stc", "default", "420", "01")
                1 -> showApnDetails("Vodafone", "vodafone", "default", "602", "01")
                2 -> showApnDetails("Etisalat", "etisalat", "default", "424", "03")
                3 -> showApnDetails("Zain", "zain", "default", "419", "02")
                4 -> showApnDetails("Ooredoo", "ooredoo", "default", "427", "01")
            }
        }.show()
    }

    private fun showApnDetails(name: String, apn: String, type: String, mcc: String, mnc: String) {
        val msg = "الاسم: $name\nAPN: $apn\nالنوع: $type\nMCC: $mcc\nMNC: $mnc"
        AlertDialog.Builder(this).setTitle("⚙️ إعدادات APN").setMessage(msg)
            .setPositiveButton("فتح الإعدادات") { _, _ ->
                startActivity(Intent(Settings.ACTION_APN_SETTINGS))
            }
            .setNegativeButton("إلغاء", null).show()
    }
}                startActivity(Intent(Settings.ACTION_APN_SETTINGS))
            }
            .setNegativeButton("إلغاء", null).show()
    }
}    private fun showApnGuide() {
        val countries = arrayOf("🇸🇦 السعودية", "🇪🇬 مصر", "🇦🇪 الإمارات", "🇰🇼 الكويت", "🇶🇦 قطر")
        AlertDialog.Builder(this).setTitle("دليل إعدادات APN").setItems(countries) { _, which ->
            when (which) {
                0 -> showApnDetails("STC", "stc", "default", "420", "01")
                1 -> showApnDetails("Vodafone", "vodafone", "default", "602", "01")
                2 -> showApnDetails("Etisalat", "etisalat", "default", "424", "03")
                3 -> showApnDetails("Zain", "zain", "default", "419", "02")
                4 -> showApnDetails("Ooredoo", "ooredoo", "default", "427", "01")
            }
        }.show()
    }
    
    private fun showApnDetails(name: String, apn: String, type: String, mcc: String, mnc: String) {
        val msg = "الاسم: $name\nAPN: $apn\nالنوع: $type\nMCC: $mcc\nMNC: $mnc"
        AlertDialog.Builder(this).setTitle("إعدادات APN").setMessage(msg)
            .setPositiveButton("فتح الإعدادات") { _, _ ->
                startActivity(Intent(Settings.ACTION_APN_SETTINGS))
            }
            .setNegativeButton("إلغاء", null).show()
    }
}
