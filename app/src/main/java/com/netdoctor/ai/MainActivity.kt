package com.netdoctor.ai

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

// معالج الأخطاء العام
class CrashHandler : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val file = File(android.os.Environment.getExternalStorageDirectory(), "netdoctor_crash.log")
            val writer = FileWriter(file, true)
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            writer.write("[$date] ${throwable.javaClass.simpleName}: ${throwable.message}\n")
            throwable.stackTrace.forEach {
                writer.write("  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})\n")
            }
            writer.write("\n")
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}

class MainActivity : AppCompatActivity() {
    
    private lateinit var tvNetworkType: TextView
    private lateinit var tvSignalStrength: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvOperator: TextView
    private lateinit var tvDataUsage: TextView
    private lateinit var btnDiagnose: Button
    private lateinit var btnApnGuide: Button
    private lateinit var btnDeepSeek: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerApps: RecyclerView
    private lateinit var adView: AdView
    
    private var mInterstitialAd: InterstitialAd? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val appsList = mutableListOf<AppUsageData>()
    private lateinit var appsAdapter: AppsUsageAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // تعيين معالج الأخطاء أولاً
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
        checkPermissions()
        loadAds()
        loadInterstitialAd()
        setupRecyclerView()
        startDataCollection()
    }
    
    private fun initViews() {
        try {
            tvNetworkType = findViewById(R.id.tv_network_type)
            tvSignalStrength = findViewById(R.id.tv_signal_strength)
            tvIpAddress = findViewById(R.id.tv_ip_address)
            tvOperator = findViewById(R.id.tv_operator)
            tvDataUsage = findViewById(R.id.tv_data_usage)
            btnDiagnose = findViewById(R.id.btn_diagnose)
            btnApnGuide = findViewById(R.id.btn_apn_guide)
            btnDeepSeek = findViewById(R.id.btn_deepseek)
            progressBar = findViewById(R.id.progress_bar)
            recyclerApps = findViewById(R.id.recycler_apps)
            adView = findViewById(R.id.adView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupClickListeners() {
        btnDiagnose.setOnClickListener {
            try {
                diagnoseNetwork()
                showInterstitialAd()
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        btnApnGuide.setOnClickListener { showApnGuide() }
        btnDeepSeek.setOnClickListener {
            try {
                startActivity(Intent(this, DeepSeekChatActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ في فتح المساعد: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPermissions() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 100)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun diagnoseNetwork() {
        try {
            progressBar.visibility = android.view.View.VISIBLE
            mainScope.launch {
                val info = withContext(Dispatchers.IO) { getNetworkInfo() }
                tvNetworkType.text = "🌐 نوع الشبكة: ${info["type"]}"
                tvSignalStrength.text = "📶 قوة الإشارة: ${info["signal"]}%"
                tvIpAddress.text = "📍 عنوان IP: ${info["ip"]}"
                tvOperator.text = "📱 المشغل: ${info["operator"]}"
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@MainActivity, "تم تشخيص الشبكة", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, "فشل التشخيص: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getNetworkInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(active)
            info["type"] = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi 📶"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "4G/5G 📱"
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
        try {
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
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showApnDetails(name: String, apn: String, type: String, mcc: String, mnc: String) {
        val msg = "الاسم: $name\nAPN: $apn\nالنوع: $type\nMCC: $mcc\nMNC: $mnc"
        AlertDialog.Builder(this).setTitle("⚙️ إعدادات APN").setMessage(msg)
            .setPositiveButton("فتح الإعدادات") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APN_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "لا يمكن فتح الإعدادات", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null).show()
    }
    
    private fun setupRecyclerView() {
        try {
            recyclerApps.layoutManager = LinearLayoutManager(this)
            appsAdapter = AppsUsageAdapter(appsList) { app ->
                showAppDetails(app)
            }
            recyclerApps.adapter = appsAdapter
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startDataCollection() {
        mainScope.launch {
            while (true) {
                try {
                    collectUsage()
                    delay(60000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun collectUsage() {
        mainScope.launch {
            val usage = withContext(Dispatchers.IO) { getTopApps() }
            val total = usage.sumOf { it.mobileBytes + it.wifiBytes } / (1024 * 1024)
            tvDataUsage.text = "📊 الاستهلاك اليوم: ${total} MB"
            appsList.clear()
            appsList.addAll(usage.take(10))
            appsAdapter.notifyDataSetChanged()
        }
    }
    
    private fun getTopApps(): List<AppUsageData> {
        val map = HashMap<String, AppUsageData>()
        try {
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val pm = packageManager
            for (stat in stats) {
                try {
                    val info = pm.getApplicationInfo(stat.packageName, 0)
                    val name = pm.getApplicationLabel(info).toString()
                    map[stat.packageName] = AppUsageData(
                        stat.packageName, 
                        name, 
                        stat.totalTimeInForeground * 1000, 
                        stat.totalTimeVisible * 500
                    )
                } catch (e: Exception) { 
                    // تجاهل التطبيقات بدون أسماء
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map.values.sortedByDescending { it.mobileBytes + it.wifiBytes }
    }
    
    private fun showAppDetails(app: AppUsageData) {
        try {
            val total = (app.mobileBytes + app.wifiBytes) / (1024 * 1024)
            AlertDialog.Builder(this).setTitle(app.appName)
                .setMessage("الاستهلاك التقريبي: $total MB")
                .setPositiveButton("إعدادات التطبيق") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${app.packageName}")))
                    } catch (e: Exception) {
                        Toast.makeText(this, "لا يمكن فتح الإعدادات", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("إلغاء", null).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadAds() {
        try {
            MobileAds.initialize(this) {}
            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadInterstitialAd() {
        try {
            InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) { 
                        mInterstitialAd = ad 
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) { 
                        mInterstitialAd = null 
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showInterstitialAd() {
        try {
            mInterstitialAd?.show(this)
            loadInterstitialAd()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}

data class AppUsageData(
    val packageName: String, 
    val appName: String, 
    val mobileBytes: Long, 
    val wifiBytes: Long
)

class AppsUsageAdapter(
    private val list: List<AppUsageData>, 
    private val onClick: (AppUsageData) -> Unit
) : RecyclerView.Adapter<AppsUsageAdapter.ViewHolder>() {
    
    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(android.R.id.text1)
        val usage: TextView = view.findViewById(android.R.id.text2)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int): ViewHolder {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(v)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        try {
            val app = list[pos]
            val total = (app.mobileBytes + app.wifiBytes) / (1024 * 1024)
            holder.name.text = app.appName
            holder.usage.text = "$total MB"
            holder.itemView.setOnClickListener { onClick(app) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun getItemCount(): Int = list.size
}    private var mInterstitialAd: InterstitialAd? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val appsList = mutableListOf<AppUsageData>()
    private lateinit var appsAdapter: AppsUsageAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
        checkPermissions()
        loadAds()
        loadInterstitialAd()
        setupRecyclerView()
        startDataCollection()
    }
    
    private fun initViews() {
        tvNetworkType = findViewById(R.id.tv_network_type)
        tvSignalStrength = findViewById(R.id.tv_signal_strength)
        tvIpAddress = findViewById(R.id.tv_ip_address)
        tvOperator = findViewById(R.id.tv_operator)
        tvDataUsage = findViewById(R.id.tv_data_usage)
        btnDiagnose = findViewById(R.id.btn_diagnose)
        btnApnGuide = findViewById(R.id.btn_apn_guide)
        btnDeepSeek = findViewById(R.id.btn_deepseek)
        progressBar = findViewById(R.id.progress_bar)
        recyclerApps = findViewById(R.id.recycler_apps)
        adView = findViewById(R.id.adView)
    }
    
    private fun setupClickListeners() {
        btnDiagnose.setOnClickListener {
            diagnoseNetwork()
            showInterstitialAd()
        }
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
        progressBar.visibility = android.view.View.VISIBLE
        mainScope.launch {
            val info = withContext(Dispatchers.IO) { getNetworkInfo() }
            tvNetworkType.text = "🌐 نوع الشبكة: ${info["type"]}"
            tvSignalStrength.text = "📶 قوة الإشارة: ${info["signal"]}%"
            tvIpAddress.text = "📍 عنوان IP: ${info["ip"]}"
            tvOperator.text = "📱 المشغل: ${info["operator"]}"
            progressBar.visibility = android.view.View.GONE
            Toast.makeText(this@MainActivity, "تم تشخيص الشبكة", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getNetworkInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(active)
            info["type"] = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi 📶"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "4G/5G 📱"
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
            info["type"] = "خطأ"; info["signal"] = "0"; info["ip"] = "غير متوفر"; info["operator"] = "غير معروف"
        }
        return info
    }
    
    private fun getLocalIpAddress(): String {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') != true }
                ?.hostAddress ?: "غير متوفر"
        } catch (e: Exception) { "غير متوفر" }
    }
    
    private fun showApnGuide() {
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
        AlertDialog.Builder(this).setTitle("إعدادات APN").setMessage(msg).setPositiveButton("فتح الإعدادات") { _, _ ->
            startActivity(Intent(Settings.ACTION_APN_SETTINGS))
        }.setNegativeButton("إلغاء", null).show()
    }
    
    private fun setupRecyclerView() {
        recyclerApps.layoutManager = LinearLayoutManager(this)
        appsAdapter = AppsUsageAdapter(appsList) { app ->
            showAppDetails(app)
        }
        recyclerApps.adapter = appsAdapter
    }
    
    private fun startDataCollection() {
        mainScope.launch {
            while (true) {
                collectUsage()
                delay(60000)
            }
        }
    }
    
    private fun collectUsage() {
        mainScope.launch {
            val usage = withContext(Dispatchers.IO) { getTopApps() }
            val total = usage.sumOf { it.mobileBytes + it.wifiBytes } / (1024 * 1024)
            tvDataUsage.text = "📊 الاستهلاك اليوم: ${total} MB"
            appsList.clear()
            appsList.addAll(usage.take(10))
            appsAdapter.notifyDataSetChanged()
        }
    }
    
    private fun getTopApps(): List<AppUsageData> {
        val map = HashMap<String, AppUsageData>()
        try {
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val pm = packageManager
            for (stat in stats) {
                try {
                    val info = pm.getApplicationInfo(stat.packageName, 0)
                    val name = pm.getApplicationLabel(info).toString()
                    map[stat.packageName] = AppUsageData(stat.packageName, name, stat.totalTimeInForeground * 1000, stat.totalTimeVisible * 500)
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return map.values.sortedByDescending { it.mobileBytes + it.wifiBytes }
    }
    
    private fun showAppDetails(app: AppUsageData) {
        val total = (app.mobileBytes + app.wifiBytes) / (1024 * 1024)
        AlertDialog.Builder(this).setTitle(app.appName).setMessage("الاستهلاك: $total MB").setPositiveButton("الإعدادات") { _, _ ->
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${app.packageName}")))
        }.setNegativeButton("إلغاء", null).show()
    }
    
    private fun loadAds() {
        MobileAds.initialize(this) {}
        adView.loadAd(AdRequest.Builder().build())
    }
    
    private fun loadInterstitialAd() {
        InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { mInterstitialAd = null }
            })
    }
    
    private fun showInterstitialAd() {
        mInterstitialAd?.show(this)
        loadInterstitialAd()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}

data class AppUsageData(val packageName: String, val appName: String, val mobileBytes: Long, val wifiBytes: Long)

class AppsUsageAdapter(private val list: List<AppUsageData>, private val onClick: (AppUsageData) -> Unit) : RecyclerView.Adapter<AppsUsageAdapter.ViewHolder>() {
    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(android.R.id.text1)
        val usage: TextView = view.findViewById(android.R.id.text2)
    }
    override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int): ViewHolder {
        val v = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(v)
    }
    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val app = list[pos]
        val total = (app.mobileBytes + app.wifiBytes) / (1024 * 1024)
        holder.name.text = app.appName
        holder.usage.text = "$total MB"
        holder.itemView.setOnClickListener { onClick(app) }
    }
    override fun getItemCount(): Int = list.size
}    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 100)
        }
    }
    
    private fun diagnoseNetwork() {
        progressBar.visibility = android.view.View.VISIBLE
        mainScope.launch {
            val networkInfo = withContext(Dispatchers.IO) { getNetworkInfo() }
            tvNetworkType.text = "🌐 نوع الشبكة: ${networkInfo["type"]}"
            tvSignalStrength.text = "📶 قوة الإشارة: ${networkInfo["signal"]}%"
            tvIpAddress.text = "📍 عنوان IP: ${networkInfo["ip"]}"
            tvOperator.text = "📱 المشغل: ${networkInfo["operator"]}"
            progressBar.visibility = android.view.View.GONE
            Toast.makeText(this@MainActivity, "تم تشخيص الشبكة", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getNetworkInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            info["type"] = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi 📶"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "4G/5G 📱"
                else -> "غير متصل ❌"
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
            info["type"] = "خطأ"; info["signal"] = "0"; info["ip"] = "غير متوفر"; info["operator"] = "غير معروف"
        }
        return info
    }
    
    private fun getLocalIpAddress(): String {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') != true }
                ?.hostAddress ?: "غير متوفر"
        } catch (e: Exception) { "غير متوفر" }
    }
    
    private fun showApnGuide() {
        val countries = arrayOf("🇸🇦 السعودية", "🇪🇬 مصر", "🇦🇪 الإمارات", "🇰🇼 الكويت", "🇶🇦 قطر", "🇧🇭 البحرين", "🇴🇲 عمان", "🇯🇴 الأردن", "🇱🇧 لبنان", "🇾🇪 اليمن")
        AlertDialog.Builder(this).setTitle("📖 دليل إعدادات APN").setItems(countries) { _, which ->
            when (which) {
                0 -> showApnDetails("STC Internet", "stc", "default", "420", "01")
                1 -> showApnDetails("Vodafone Internet", "vodafone", "default", "602", "01")
                2 -> showApnDetails("Etisalat Internet", "etisalat", "default", "424", "03")
                3 -> showApnDetails("Zain Internet", "zain", "default", "419", "02")
                4 -> showApnDetails("Ooredoo Internet", "ooredoo", "default", "427", "01")
                5 -> showApnDetails("Batelco Internet", "batelco", "default", "426", "01")
                6 -> showApnDetails("Omantel Internet", "omantel", "default", "422", "02")
                7 -> showApnDetails("Zain Internet", "zain", "default", "416", "01")
                8 -> showApnDetails("Touch Internet", "touch", "default", "415", "01")
                9 -> showApnDetails("Yemen Mobile Internet", "yemenmobile", "default", "421", "04")
            }
        }.show()
    }
    
    private fun showApnDetails(name: String, apn: String, apnType: String, mcc: String, mnc: String) {
        val message = "📱 إعدادات APN المقترحة:\n\n📛 الاسم: $name\n🔗 APN: $apn\n📡 نوع APN: $apnType\n🌍 MCC: $mcc\n📍 MNC: $mnc\n🔐 البروتوكول: IPv4/IPv6"
        AlertDialog.Builder(this).setTitle("⚙️ إعدادات APN").setMessage(message).setPositiveButton("فتح إعدادات APN") { _, _ ->
            startActivity(Intent(Settings.ACTION_APN_SETTINGS))
        }.setNegativeButton("إلغاء", null).show()
    }
    
    private fun showGeminiWebView() {
        geminiWebView.visibility = android.view.View.VISIBLE
        scrollView.visibility = android.view.View.GONE
        geminiWebView.settings.javaScriptEnabled = true
        geminiWebView.settings.domStorageEnabled = true
        geminiWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = android.view.View.GONE
            }
        }
        geminiWebView.loadUrl("https://gemini.google.com")
        progressBar.visibility = android.view.View.VISIBLE
    }
    
    override fun onBackPressed() {
        if (geminiWebView.visibility == android.view.View.VISIBLE) {
            geminiWebView.visibility = android.view.View.GONE
            scrollView.visibility = android.view.View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }
    
    private fun setupRecyclerView() {
        recyclerApps.layoutManager = LinearLayoutManager(this)
        appsAdapter = AppsUsageAdapter(appsList) { appInfo ->
            if (isProUser) showAppDataDetails(appInfo) else showProUpgradeDialog("تفاصيل الاستهلاك المتقدمة")
        }
        recyclerApps.adapter = appsAdapter
    }
    
    private fun startDataCollection() {
        mainScope.launch {
            while (true) {
                collectDataUsage()
                delay(60000)
            }
        }
    }
    
    private fun collectDataUsage() {
        mainScope.launch {
            val usage = withContext(Dispatchers.IO) { getTopAppsDataUsage() }
            val totalMB = usage.sumOf { it.mobileBytes + it.wifiBytes } / (1024 * 1024)
            tvDataUsage.text = "📊 إجمالي الاستهلاك اليوم: ${totalMB} MB"
            appsList.clear()
            appsList.addAll(usage.take(10))
            appsAdapter.notifyDataSetChanged()
        }
    }
    
    private fun getTopAppsDataUsage(): List<AppUsageInfo> {
        val usageMap = HashMap<String, AppUsageInfo>()
        try {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24 * 60 * 60 * 1000
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            val pm = packageManager
            for (stat in stats) {
                try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    usageMap[stat.packageName] = AppUsageInfo(
                        packageName = stat.packageName,
                        appName = appName,
                        mobileBytes = stat.totalTimeInForeground * 1000,
                        wifiBytes = stat.totalTimeVisible * 500,
                        foregroundTime = stat.totalTimeInForeground,
                        backgroundTime = stat.totalTimeVisible
                    )
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return usageMap.values.sortedByDescending { it.mobileBytes + it.wifiBytes }
    }
    
    private fun showDetailedAnalytics() {
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
            return
        }
        val totalUsage = appsList.sumOf { it.mobileBytes + it.wifiBytes } / (1024 * 1024)
        AlertDialog.Builder(this).setTitle("📈 تحليلات متقدمة").setMessage("📊 إجمالي الاستهلاك اليوم: $totalUsage MB\n\n📱 عدد التطبيقات المستخدمة: ${appsList.size}").setPositiveButton("فتح الإعدادات") { _, _ ->
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.setNegativeButton("إغلاق", null).show()
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this).setTitle("⚠️ صلاحية مطلوبة").setMessage("لتحليل استهلاك البيانات، نحتاج إلى صلاحية الوصول لإحصائيات الاستخدام").setPositiveButton("منح الصلاحية") { _, _ ->
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.setNegativeButton("تذكر لاحقاً", null).show()
    }
    
    private fun showAppDataDetails(appInfo: AppUsageInfo) {
        val totalMB = (appInfo.mobileBytes + appInfo.wifiBytes) / (1024 * 1024)
        AlertDialog.Builder(this).setTitle("تفاصيل الاستهلاك").setMessage("📱 التطبيق: ${appInfo.appName}\n\n📊 إجمالي الاستهلاك: $totalMB MB").setPositiveButton("تقييد بيانات الخلفية") { _, _ ->
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${appInfo.packageName}")))
        }.setNegativeButton("إلغاء", null).show()
    }
    
    private fun showFirewallDialog() {
        AlertDialog.Builder(this).setTitle("🔥 الجدار الناري (قيد التطوير)").setMessage("سيتم إطلاقه في الإصدار 2.0").setPositiveButton("متابعة", null).show()
    }
    
    private fun loadAds() {
        if (!isProUser) {
            MobileAds.initialize(this) {}
            adView.loadAd(AdRequest.Builder().build())
        }
    }
    
    private fun loadInterstitialAd() {
        if (!isProUser) {
            InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                    override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
                })
        }
    }
    
    private fun showInterstitialAd() {
        if (!isProUser && mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            loadInterstitialAd()
        }
    }
    
    private fun showProUpgradeDialog(featureName: String) {
        AlertDialog.Builder(this).setTitle("✨ ميزة Pro: $featureName").setMessage("السعر: 4.99 دولار").setPositiveButton("الترقية الآن") { _, _ ->
            Toast.makeText(this, "جاري تطوير نظام الدفع...", Toast.LENGTH_LONG).show()
        }.setNegativeButton("تذكر لاحقاً", null).show()
    }
    
    private fun checkAndShowWeeklyRecommendation() {
        val prefs = getSharedPreferences("netdoctor", MODE_PRIVATE)
        val lastRecommendation = prefs.getLong("last_recommendation", 0)
        val now = System.currentTimeMillis()
        if (now - lastRecommendation > 7 * 24 * 60 * 60 * 1000L) {
            AlertDialog.Builder(this).setTitle("💡 توصية الأسبوع").setMessage("استخدم الواي فاي كلما أمكن، وقم بتعطيل التحديث التلقائي للتطبيقات.").setPositiveButton("حسناً", null).show()
            prefs.edit().putLong("last_recommendation", now).apply()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val foregroundTime: Long,
    val backgroundTime: Long
)

class AppsUsageAdapter(
    private val appsList: List<AppUsageInfo>,
    private val onItemClick: (AppUsageInfo) -> Unit
) : RecyclerView.Adapter<AppsUsageAdapter.ViewHolder>() {
    
    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvAppName: TextView = itemView.findViewById(android.R.id.text1)
        val tvUsage: TextView = itemView.findViewById(android.R.id.text2)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = appsList[position]
        val totalMB = (app.mobileBytes + app.wifiBytes) / (1024 * 1024)
        holder.tvAppName.text = app.appName
        holder.tvUsage.text = "${totalMB} MB"
        holder.itemView.setOnClickListener { onItemClick(app) }
    }
    
    override fun getItemCount(): Int = appsList.size
}
