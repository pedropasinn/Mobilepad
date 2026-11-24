package io.github.pedropasinn.mobilepad

import org.json.JSONArray
import org.json.JSONObject

/**
 * A game's setup, saved under a name: skin, layout for each orientation, button names, button
 * remapping, macros and wheels, motion settings. Every value is kept in the same text form the
 * preferences already use, so a profile is just a bundle of them.
 */
data class GameProfile(
    val id: String,
    val name: String,
    /** Built-in skin (SkinId name). */
    val skin: String,
    /** Imported skin in use (its folder name), or "" for the built-in one. */
    val deltaSkin: String,
    val layoutLand: String,
    val layoutPort: String,
    val buttonNames: String,
    val remap: String,
    val macros: String,
    val motion: String,
    /** The other layouts (see Prefs.altLandscape). */
    val altLand: String = "",
    val altPort: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("skin", skin).put("deltaSkin", deltaSkin)
        .put("layoutLand", layoutLand).put("layoutPort", layoutPort).put("buttonNames", buttonNames)
        .put("remap", remap).put("macros", macros).put("motion", motion)
        .put("altLand", altLand).put("altPort", altPort)

    /** Same setup, whatever the name and id. */
    fun sameSetup(other: GameProfile) = copy(id = "", name = "") == other.copy(id = "", name = "")

    companion object {
        fun fromJson(o: JSONObject) = GameProfile(
            o.optString("id"), o.optString("name"), o.optString("skin"), o.optString("deltaSkin"),
            o.optString("layoutLand"), o.optString("layoutPort"), o.optString("buttonNames"),
            o.optString("remap"), o.optString("macros"), o.optString("motion"),
            o.optString("altLand"), o.optString("altPort"),
        )

        fun listToJson(list: List<GameProfile>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String?): List<GameProfile> = runCatching {
            val a = JSONArray(s ?: "[]")
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }.filter { it.id.isNotEmpty() }
        }.getOrDefault(emptyList())

        fun newId() = "p" + System.currentTimeMillis().toString(36)
    }
}
