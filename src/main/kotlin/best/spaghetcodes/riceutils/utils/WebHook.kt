package best.spaghetcodes.riceutils.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object WebHook {

    fun sendEmbed(url: String, embed: JsonObject) {
        Thread {
            try {
                val body = JsonObject()
                body.addProperty("content", "")
                body.addProperty("username", "RiceUtils")
                body.addProperty("avatar_url", "https://raw.githubusercontent.com/HumanDuck23/upload-stuff-here/main/duck_dueller.png")

                val arr = JsonArray()
                arr.add(embed)
                body.add("embeds", arr)

                println("[RiceUtils] Sending webhook...")
                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.addRequestProperty("Content-Type", "application/json")
                connection.addRequestProperty("User-Agent", "RiceUtils-Webhook")
                connection.doOutput = true
                connection.requestMethod = "POST"

                DataOutputStream(connection.outputStream).use { it.writeBytes(body.toString()) }
                BufferedReader(InputStreamReader(connection.inputStream)).use { bf ->
                    var line: String?
                    while (bf.readLine().also { line = it } != null) {
                        println(line)
                    }
                }
                println("[RiceUtils] Webhook sent successfully!")
            } catch (e: Exception) {
                println("[RiceUtils] Webhook error: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    fun buildEmbed(title: String, description: String, fields: JsonArray, footer: JsonObject, color: Int): JsonObject {
        val obj = JsonObject()
        obj.addProperty("title", title)
        if (description.isNotEmpty())
            obj.addProperty("description", description)
        obj.addProperty("color", color)
        obj.add("footer", footer)
        obj.add("fields", fields)
        return obj
    }

    fun buildFields(fields: ArrayList<Map<String, String>>): JsonArray {
        val arr = JsonArray()
        for (field in fields) {
            val obj = JsonObject()
            obj.addProperty("name", field["name"])
            obj.addProperty("value", field["value"])
            obj.addProperty("inline", field["inline"] == "true")
            arr.add(obj)
        }
        return arr
    }

    fun buildFooter(text: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("text", text)
        return obj
    }
}


