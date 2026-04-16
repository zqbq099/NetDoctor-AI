package com.netdoctor.ai

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var thinkingLayout: android.view.View
    private lateinit var tvThinking: TextView

    private val messages = mutableListOf<ChatMessage>()
    private val chatAdapter = ChatAdapter(messages)
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private var thinkingJob: Job? = null

    private val thinkingMessages = listOf(
        "🔍 جاري تحليل مشكلتك...",
        "🧠 جاري البحث في قاعدة البيانات...",
        "📡 جاري الاتصال بخادم DeepSeek...",
        "✍️ جاري كتابة الرد..."
    )

    // ضع مفتاح API الخاص بك هنا
    private val API_KEY = "sk-169ca86c37ca498a90829a11dfefc4b7"
    private val API_URL = "https://api.deepseek.com/v1/chat/completions"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deepseek_chat)

        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        progressBar = findViewById(R.id.progressBar)
        thinkingLayout = findViewById(R.id.thinkingLayout)
        tvThinking = findViewById(R.id.tvThinking)

        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }

        messages.add(ChatMessage("مرحباً! أنا مساعد NetDoctor الذكي. كيف يمكنني مساعدتك اليوم؟", false))
        chatAdapter.notifyDataSetChanged()
    }

    private fun sendMessage(message: String) {
        messages.add(ChatMessage(message, true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        etMessage.text.clear()
        rvChat.smoothScrollToPosition(messages.size - 1)

        startThinking()

        mainScope.launch {
            val reply = withContext(Dispatchers.IO) {
                callDeepSeekAPI(message)
            }
            stopThinking()
            messages.add(ChatMessage(reply, false))
            chatAdapter.notifyItemInserted(messages.size - 1)
            rvChat.smoothScrollToPosition(messages.size - 1)
        }
    }

    private fun startThinking() {
        thinkingLayout.visibility = android.view.View.VISIBLE
        var index = 0
        tvThinking.text = thinkingMessages[index]
        thinkingJob = mainScope.launch {
            while (index < thinkingMessages.size - 1) {
                delay(1200)
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
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $API_KEY")
            connection.doOutput = true

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

            connection.outputStream.write(jsonBody.toString().toByteArray())
            connection.outputStream.flush()

            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            connection.disconnect()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(response.toString())
                jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                "عذراً، حدث خطأ: $responseCode"
            }
        } catch (e: Exception) {
            "خطأ: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
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
}
