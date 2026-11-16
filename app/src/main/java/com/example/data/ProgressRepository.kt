package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CargoType
import com.example.model.DriverCharacter
import com.example.model.MapEnvironment
import com.example.model.PlayerProgress
import com.example.model.TractorModel
import com.example.model.TractorUpgrades
import com.example.model.TrailerType
import com.example.model.UpgradeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anshtrax_progress")

class ProgressRepository(private val context: Context) {

    private object Keys {
        val COINS = intPreferencesKey("coins")
        val SELECTED_TRACTOR = stringPreferencesKey("selected_tractor")
        val SELECTED_DRIVER = stringPreferencesKey("selected_driver")
        val SELECTED_TRAILER = stringPreferencesKey("selected_trailer")
        val SELECTED_CARGO = stringPreferencesKey("selected_cargo")
        val SELECTED_MAP = stringPreferencesKey("selected_map")
        val UNLOCKED_TRACTORS = stringSetPreferencesKey("unlocked_tractors")
        val UNLOCKED_TRAILERS = stringSetPreferencesKey("unlocked_trailers")
        val UNLOCKED_CARGOS = stringSetPreferencesKey("unlocked_cargos")
        val UNLOCKED_MAPS = stringSetPreferencesKey("unlocked_maps")
        val COMPLETED_MISSIONS = stringSetPreferencesKey("completed_missions")
        val TRACTOR_UPGRADES_JSON = stringPreferencesKey("tractor_upgrades_json")
        val BEST_DISTANCES_JSON = stringPreferencesKey("best_distances_json")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
    }

    val progressFlow: Flow<PlayerProgress> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        val default = PlayerProgress()
        val tractorId = prefs[Keys.SELECTED_TRACTOR] ?: default.selectedTractor.id
        val driverId = prefs[Keys.SELECTED_DRIVER] ?: default.selectedDriver.id
        val trailerId = prefs[Keys.SELECTED_TRAILER] ?: default.selectedTrailer.id
        val cargoId = prefs[Keys.SELECTED_CARGO] ?: default.selectedCargo.id
        val mapId = prefs[Keys.SELECTED_MAP] ?: default.selectedMap.id

        PlayerProgress(
            coins = prefs[Keys.COINS] ?: default.coins,
            selectedTractor = TractorModel.entries.find { it.id == tractorId } ?: default.selectedTractor,
            selectedDriver = DriverCharacter.entries.find { it.id == driverId } ?: default.selectedDriver,
            selectedTrailer = TrailerType.entries.find { it.id == trailerId } ?: default.selectedTrailer,
            selectedCargo = CargoType.entries.find { it.id == cargoId } ?: default.selectedCargo,
            selectedMap = MapEnvironment.entries.find { it.id == mapId } ?: default.selectedMap,
            unlockedTractors = prefs[Keys.UNLOCKED_TRACTORS] ?: default.unlockedTractors,
            unlockedTrailers = prefs[Keys.UNLOCKED_TRAILERS] ?: default.unlockedTrailers,
            unlockedCargos = prefs[Keys.UNLOCKED_CARGOS] ?: default.unlockedCargos,
            unlockedMaps = prefs[Keys.UNLOCKED_MAPS] ?: default.unlockedMaps,
            completedMissions = prefs[Keys.COMPLETED_MISSIONS] ?: emptySet(),
            tractorUpgrades = decodeUpgrades(prefs[Keys.TRACTOR_UPGRADES_JSON]),
            bestDistances = decodeBestDistances(prefs[Keys.BEST_DISTANCES_JSON]),
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: true,
            sfxEnabled = prefs[Keys.SFX_ENABLED] ?: true
        )
    }

    suspend fun saveProgress(progress: PlayerProgress) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COINS] = progress.coins
            prefs[Keys.SELECTED_TRACTOR] = progress.selectedTractor.id
            prefs[Keys.SELECTED_DRIVER] = progress.selectedDriver.id
            prefs[Keys.SELECTED_TRAILER] = progress.selectedTrailer.id
            prefs[Keys.SELECTED_CARGO] = progress.selectedCargo.id
            prefs[Keys.SELECTED_MAP] = progress.selectedMap.id
            prefs[Keys.UNLOCKED_TRACTORS] = progress.unlockedTractors
            prefs[Keys.UNLOCKED_TRAILERS] = progress.unlockedTrailers
            prefs[Keys.UNLOCKED_CARGOS] = progress.unlockedCargos
            prefs[Keys.UNLOCKED_MAPS] = progress.unlockedMaps
            prefs[Keys.COMPLETED_MISSIONS] = progress.completedMissions
            prefs[Keys.TRACTOR_UPGRADES_JSON] = encodeUpgrades(progress.tractorUpgrades)
            prefs[Keys.BEST_DISTANCES_JSON] = encodeBestDistances(progress.bestDistances)
            prefs[Keys.MUSIC_ENABLED] = progress.musicEnabled
            prefs[Keys.SFX_ENABLED] = progress.sfxEnabled
        }
    }

    private fun encodeUpgrades(map: Map<String, TractorUpgrades>): String {
        val root = JSONObject()
        map.forEach { (tractorId, upgrades) ->
            val obj = JSONObject()
            upgrades.levels.forEach { (type, lvl) ->
                obj.put(type.id, lvl)
            }
            root.put(tractorId, obj)
        }
        return root.toString()
    }

    private fun decodeUpgrades(jsonStr: String?): Map<String, TractorUpgrades> {
        val result = TractorModel.entries.associate { it.id to TractorUpgrades() }.toMutableMap()
        if (jsonStr.isNullOrBlank()) return result
        return try {
            val root = JSONObject(jsonStr)
            TractorModel.entries.forEach { tractor ->
                val obj = root.optJSONObject(tractor.id)
                if (obj != null) {
                    val lvls = UpgradeType.entries.associateWith { type ->
                        obj.optInt(type.id, 1).coerceIn(1, type.maxLevel)
                    }
                    result[tractor.id] = TractorUpgrades(lvls)
                }
            }
            result
        } catch (_: Exception) {
            result
        }
    }

    private fun encodeBestDistances(map: Map<String, Int>): String {
        val root = JSONObject()
        map.forEach { (k, v) -> root.put(k, v) }
        return root.toString()
    }

    private fun decodeBestDistances(jsonStr: String?): Map<String, Int> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(jsonStr)
            val out = mutableMapOf<String, Int>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                out[key] = root.optInt(key, 0)
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
