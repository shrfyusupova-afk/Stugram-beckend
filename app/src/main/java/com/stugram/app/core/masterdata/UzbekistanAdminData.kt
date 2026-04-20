package com.stugram.app.core.masterdata

import android.content.Context
import com.google.gson.Gson

data class UzbekistanAdminDataset(
    val regions: List<UzRegion>
)

data class UzRegion(
    val name: String,
    val districts: List<UzDistrict>
)

data class UzDistrict(
    val type: String, // "tuman" | "shahar"
    val name: String,
    val schools: List<String>
)

object UzbekistanAdminData {
    private const val ASSET_NAME = "uzbekistan_admin.json"
    private val gson = Gson()

    @Volatile
    private var cached: UzbekistanAdminDataset? = null

    fun get(context: Context): UzbekistanAdminDataset {
        val existing = cached
        if (existing != null) return existing

        val loaded = context.assets.open(ASSET_NAME).use { input ->
            val json = input.bufferedReader().use { it.readText() }
            gson.fromJson(json, UzbekistanAdminDataset::class.java)
        }
        cached = loaded
        return loaded
    }

    fun regions(context: Context): List<String> = get(context).regions.map { it.name }

    fun districts(context: Context, regionName: String): List<String> {
        val region = get(context).regions.firstOrNull { it.name == regionName } ?: return emptyList()
        return region.districts.map { it.name }
    }

    fun schools(context: Context, regionName: String, districtName: String): List<String> {
        val region = get(context).regions.firstOrNull { it.name == regionName } ?: return emptyList()
        val district = region.districts.firstOrNull { it.name == districtName } ?: return emptyList()
        return district.schools
    }
}

