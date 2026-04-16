package com.netdoctor.ai

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

class DeepSeekChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvThinking: TextView
    private lateinit var thinkingLayout: android.view.View

    private val chatAdapter = ChatMessageAdapter()
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private var thinkingJob: Job? = null
    
    private val thinkingMessages = listOf(
        "🔍 جاري تحليل مشكلتك...",
        "🧠 جاري البحث في قاعدة البيانات...",
        "📡 جاري الاتصال بخادم DeepSeek...",
        "⚙️ جاري تجهيز الإجابة المناسبة...",
        "✍️ جاري كتابة الرد..."
    )

    // استبدل هذا بمفتاح API الخاص بك
    private val API_KEY = "YOUR_DEEPSEEK_API_KEY_HERE"
    private val API_URL = "https://api.deepseek.com/v1/chat/completions"

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deepseek_chat)

        initViews()
        setupRecyclerView()
        setupListeners()
        
        chatAdapter.addMessage(ChatMessage("مرحباً! أنا مساعد NetDoctor الذكي. كيف يمكنني مساعدتك اليوم؟", false))
    }

    private fun initViews() {
        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        progressBar = findViewById(R.id.progressBar)
        tvThinking = findViewById(R.id.tvThinking)
        thinkingLayout = findViewById(R.id.thinkingLayout)
    }

    private fun setupRecyclerView() {
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter
    }

    private fun setupListeners() {
        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
    }

    private fun sendMessage(message: String) {
        chatAdapter.addMessage(ChatMessage(message, true))
        etMessage.text.clear()
        
        startThinking()
        
        mainScope.launch {
            val reply = withContext(Dispatchers.IO) {
                callDeepSeekAPI(message)
            }
            stopThinking()
            chatAdapter.addMessage(ChatMessage(reply, false))
            rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun startThinking() {
        thinkingLayout.visibility = android.view.View.VISIBLE
        tvThinking.text = thinkingMessages[0]
        
        var index = 0
        thinkingJob = mainScope.launch {
            while (index < thinkingMessages.size - 1) {
                delay(1500)
                index++
                tvThinking.text = thinkingMessages[index]
            }
        }
    }

    private fun stopThinking() {
        thinkingJob?.cancel()
        thinkingLayout.visibility = android.view.View.GONE
    }

    private fun callDeepSeekAPI(userMessage: String): String {
        return try {
            val jsonBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("stream", false)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $API_KEY")
                .post(RequestBody.create(MediaType.parse("application/json"), jsonBody.toString()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                "عذراً، حدث خطأ: ${response.code}"
            }
        } catch (e: Exception) {
            "خطأ في الاتصال: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatMessageAdapter : RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.textView.text = message.text
        holder.textView.setTextColor(if (message.isUser) android.graphics.Color.BLUE else android.graphics.Color.BLACK)
    }

    override fun getItemCount(): Int = messages.size

    inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val textView: android.widget.TextView = view.findViewById(android.R.id.text1)
    }
}    private fun callDeepSeekAPI(userMessage: String): String {
        return try {
            val jsonBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("stream", false)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $API_KEY")
                .post(RequestBody.create(MediaType.parse("application/json"), jsonBody.toString()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                "عذراً، حدث خطأ: ${response.code}"
            }
        } catch (e: Exception) {
            "خطأ في الاتصال: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}

data class Message(val text: String, val isUser: Boolean)

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    private val messages = mutableListOf<Message>()

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.textView.text = message.text
        holder.textView.setTextColor(if (message.isUser) android.graphics.Color.BLUE else android.graphics.Color.BLACK)
    }

    override fun getItemCount(): Int = messages.size

    inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val textView: android.widget.TextView = view.findViewById(android.R.id.text1)
    }
}
