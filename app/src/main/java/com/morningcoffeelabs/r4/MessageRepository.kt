package com.morningcoffeelabs.r4

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MessageRepository(context: Context) {
    private val preferences = context.getSharedPreferences("r4_messages", Context.MODE_PRIVATE)

    fun loadMessages(): List<Message> {
        val raw = preferences.getString(KEY_MESSAGES, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val message = runCatching {
                        Message(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            text = item.getString("text"),
                            createdAt = item.getLong("createdAt"),
                            updatedAt = item.getLong("updatedAt"),
                        )
                    }.getOrNull()

                    if (message != null) {
                        add(message)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMessages(messages: List<Message>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("title", message.title)
                    .put("text", message.text)
                    .put("createdAt", message.createdAt)
                    .put("updatedAt", message.updatedAt)
            )
        }

        preferences.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    companion object {
        private const val KEY_MESSAGES = "messages"
    }
}
