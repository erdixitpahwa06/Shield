package com.shielddp.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Server(val id: String, val label: String, val config: String) {
    val configured get() = config.isNotBlank()
    val isDefault get() = id == "mumbai" || id == "hyderabad"
}

class ServerStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("shielddp", Context.MODE_PRIVATE)

    fun servers(): List<Server> {
        val raw = sp.getString("servers", null)
        val saved = mutableListOf<Server>()
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                saved += Server(o.getString("id"), o.getString("label"), o.getString("config"))
            }
        }
        // India defaults are always present
        val out = mutableListOf<Server>()
        out += saved.firstOrNull { it.id == "mumbai" } ?: Server("mumbai", "Mumbai, India", "")
        out += saved.firstOrNull { it.id == "hyderabad" } ?: Server("hyderabad", "Hyderabad, India", "")
        out += saved.filter { !it.isDefault }
        return out
    }

    fun save(list: List<Server>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("label", it.label).put("config", it.config))
        }
        sp.edit().putString("servers", arr.toString()).apply()
    }

    var selectedId: String
        get() = sp.getString("selected", "mumbai") ?: "mumbai"
        set(v) = sp.edit().putString("selected", v).apply()

    var bypass: Set<String>
        get() = sp.getStringSet("bypass", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("bypass", v).apply()
}
