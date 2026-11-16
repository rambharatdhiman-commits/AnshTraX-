package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SfxType
import com.example.audio.SoundEngine
import com.example.data.ProgressRepository
import com.example.engine.PhysicsWorld
import com.example.engine.RunPhase
import com.example.model.CargoType
import com.example.model.DriverCharacter
import com.example.model.GameScreen
import com.example.model.MapEnvironment
import com.example.model.MissionDef
import com.example.model.PlayerProgress
import com.example.model.TractorModel
import com.example.model.TractorUpgrades
import com.example.model.TrailerType
import com.example.model.UpgradeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProgressRepository(application.applicationContext)
    val soundEngine = SoundEngine(application.applicationContext)

    private val _progress = MutableStateFlow(PlayerProgress())
    val progress: StateFlow<PlayerProgress> = _progress.asStateFlow()

    private val _screen = MutableStateFlow(GameScreen.MAIN_MENU)
    val screen: StateFlow<GameScreen> = _screen.asStateFlow()

    private val _activeWorld = MutableStateFlow<PhysicsWorld?>(null)
    val activeWorld: StateFlow<PhysicsWorld?> = _activeWorld.asStateFlow()

    private val _activeMission = MutableStateFlow<MissionDef?>(null)
    val activeMission: StateFlow<MissionDef?> = _activeMission.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private var runRewardsCommitted = false

    init {
        soundEngine.start()
        viewModelScope.launch {
            runCatching {
                val loaded = repo.progressFlow.first()
                _progress.value = loaded
                soundEngine.musicEnabled = loaded.musicEnabled
                soundEngine.sfxEnabled = loaded.sfxEnabled
            }
        }
    }

    private fun updateAndPersist(transform: (PlayerProgress) -> PlayerProgress) {
        val updated = transform(_progress.value)
        _progress.value = updated
        viewModelScope.launch {
            runCatching { repo.saveProgress(updated) }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun navigateTo(target: GameScreen) {
        soundEngine.playSfx(SfxType.CLICK)
        if (target != GameScreen.GAMEPLAY) {
            soundEngine.inGameplay = false
            soundEngine.isPaused = false
            soundEngine.hornActive = false
            _activeWorld.value = null
        }
        _screen.value = target
    }

    fun toggleMusic() {
        soundEngine.playSfx(SfxType.CLICK)
        updateAndPersist { cur ->
            val next = !cur.musicEnabled
            soundEngine.musicEnabled = next
            cur.copy(musicEnabled = next)
        }
    }

    fun toggleSfx() {
        updateAndPersist { cur ->
            val next = !cur.sfxEnabled
            soundEngine.sfxEnabled = next
            if (next) soundEngine.playSfx(SfxType.CLICK)
            cur.copy(sfxEnabled = next)
        }
    }

    fun selectOrUnlockTractor(tractor: TractorModel) {
        val cur = _progress.value
        if (tractor.id in cur.unlockedTractors) {
            soundEngine.playSfx(SfxType.CLICK)
            updateAndPersist { it.copy(selectedTractor = tractor) }
        } else if (cur.coins >= tractor.unlockCost) {
            soundEngine.playSfx(SfxType.CHECKPOINT)
            updateAndPersist {
                it.copy(
                    coins = it.coins - tractor.unlockCost,
                    unlockedTractors = it.unlockedTractors + tractor.id,
                    selectedTractor = tractor
                )
            }
            _feedbackMessage.value = "${tractor.displayName} unlocked!"
        } else {
            soundEngine.playSfx(SfxType.BUMP)
            _feedbackMessage.value = "Need ${tractor.unlockCost - cur.coins} more coins for ${tractor.displayName}"
        }
    }

    fun selectDriver(driver: DriverCharacter) {
        soundEngine.playSfx(SfxType.CLICK)
        updateAndPersist { it.copy(selectedDriver = driver) }
    }

    fun selectOrUnlockTrailer(trailer: TrailerType) {
        val cur = _progress.value
        if (trailer.id in cur.unlockedTrailers) {
            soundEngine.playSfx(SfxType.CLICK)
            updateAndPersist {
                val nextCargo = if (trailer == TrailerType.NONE) CargoType.NONE else it.selectedCargo
                it.copy(selectedTrailer = trailer, selectedCargo = nextCargo)
            }
        } else if (cur.coins >= trailer.unlockCost) {
            soundEngine.playSfx(SfxType.CHECKPOINT)
            updateAndPersist {
                it.copy(
                    coins = it.coins - trailer.unlockCost,
                    unlockedTrailers = it.unlockedTrailers + trailer.id,
                    selectedTrailer = trailer
                )
            }
            _feedbackMessage.value = "${trailer.displayName} unlocked!"
        } else {
            soundEngine.playSfx(SfxType.BUMP)
            _feedbackMessage.value = "Need ${trailer.unlockCost - cur.coins} more coins"
        }
    }

    fun selectOrUnlockCargo(cargo: CargoType) {
        val cur = _progress.value
        if (cargo.id in cur.unlockedCargos) {
            soundEngine.playSfx(SfxType.CLICK)
            updateAndPersist {
                val nextTrailer = if (cargo != CargoType.NONE && it.selectedTrailer == TrailerType.NONE) {
                    TrailerType.SMALL_TROLLEY
                } else it.selectedTrailer
                it.copy(selectedCargo = cargo, selectedTrailer = nextTrailer)
            }
        } else if (cur.coins >= cargo.unlockCost) {
            soundEngine.playSfx(SfxType.CHECKPOINT)
            updateAndPersist {
                val nextTrailer = if (it.selectedTrailer == TrailerType.NONE) {
                    TrailerType.SMALL_TROLLEY
                } else it.selectedTrailer
                it.copy(
                    coins = it.coins - cargo.unlockCost,
                    unlockedCargos = it.unlockedCargos + cargo.id,
                    selectedCargo = cargo,
                    selectedTrailer = nextTrailer
                )
            }
            _feedbackMessage.value = "${cargo.displayName} unlocked!"
        } else {
            soundEngine.playSfx(SfxType.BUMP)
            _feedbackMessage.value = "Need ${cargo.unlockCost - cur.coins} more coins"
        }
    }

    fun purchaseUpgrade(type: UpgradeType) {
        val cur = _progress.value
        val tractor = cur.selectedTractor
        val tractorUpgrades = cur.upgradesFor(tractor)
        val currentLevel = tractorUpgrades.levelOf(type)
        if (currentLevel >= type.maxLevel) {
            _feedbackMessage.value = "${type.displayName} is already MAX level!"
            return
        }
        val cost = type.upgradeCost(currentLevel)
        if (cur.coins >= cost) {
            soundEngine.playSfx(SfxType.COIN)
            updateAndPersist { p ->
                val oldMap = p.upgradesFor(tractor).levels.toMutableMap()
                oldMap[type] = currentLevel + 1
                val updatedMap = p.tractorUpgrades.toMutableMap()
                updatedMap[tractor.id] = TractorUpgrades(oldMap)
                p.copy(
                    coins = p.coins - cost,
                    tractorUpgrades = updatedMap
                )
            }
            _feedbackMessage.value = "${type.displayName} upgraded to Lv.${currentLevel + 1}!"
        } else {
            soundEngine.playSfx(SfxType.BUMP)
            _feedbackMessage.value = "Need ${cost - cur.coins} more coins for ${type.displayName}"
        }
    }

    fun selectOrUnlockMap(map: MapEnvironment) {
        val cur = _progress.value
        if (map.id in cur.unlockedMaps) {
            soundEngine.playSfx(SfxType.CLICK)
            updateAndPersist { it.copy(selectedMap = map) }
        } else if (cur.coins >= map.unlockCost) {
            soundEngine.playSfx(SfxType.CHECKPOINT)
            updateAndPersist {
                it.copy(
                    coins = it.coins - map.unlockCost,
                    unlockedMaps = it.unlockedMaps + map.id,
                    selectedMap = map
                )
            }
            _feedbackMessage.value = "${map.displayName} unlocked!"
        } else {
            soundEngine.playSfx(SfxType.BUMP)
            _feedbackMessage.value = "Need ${map.unlockCost - cur.coins} more coins for ${map.displayName}"
        }
    }

    fun startFreeDrive(mapOverride: MapEnvironment? = null) {
        val cur = _progress.value
        val map = mapOverride ?: cur.selectedMap
        if (map.id !in cur.unlockedMaps) {
            selectOrUnlockMap(map)
            return
        }
        if (map != cur.selectedMap) {
            updateAndPersist { it.copy(selectedMap = map) }
        }
        _activeMission.value = null
        launchWorld(cur.copy(selectedMap = map), mission = null)
    }

    fun startMission(mission: MissionDef) {
        val cur = _progress.value
        // Ensure map & cargo & trailer are ready for the mission
        val trailer = if (cur.selectedTrailer == TrailerType.NONE) TrailerType.SMALL_TROLLEY else cur.selectedTrailer
        val patched = cur.copy(
            selectedMap = mission.map,
            selectedTrailer = trailer,
            selectedCargo = mission.requiredCargo,
            unlockedMaps = cur.unlockedMaps + mission.map.id,
            unlockedCargos = cur.unlockedCargos + mission.requiredCargo.id
        )
        _progress.value = patched
        viewModelScope.launch { repo.saveProgress(patched) }

        _activeMission.value = mission
        launchWorld(patched, mission)
    }

    private fun launchWorld(p: PlayerProgress, mission: MissionDef?) {
        runRewardsCommitted = false
        val world = PhysicsWorld(
            tractor = p.selectedTractor,
            driver = p.selectedDriver,
            trailer = p.selectedTrailer,
            cargo = p.selectedCargo,
            map = p.selectedMap,
            upgrades = p.upgradesFor(p.selectedTractor),
            mission = mission,
            soundEngine = soundEngine
        )
        _activeWorld.value = world
        soundEngine.inGameplay = true
        soundEngine.isPaused = false
        _screen.value = GameScreen.GAMEPLAY
    }

    fun pauseGameplay() {
        val w = _activeWorld.value ?: return
        if (w.phase == RunPhase.RUNNING) {
            w.phase = RunPhase.PAUSED
            soundEngine.isPaused = true
            soundEngine.hornActive = false
        }
    }

    fun resumeGameplay() {
        val w = _activeWorld.value ?: return
        if (w.phase == RunPhase.PAUSED) {
            w.phase = RunPhase.RUNNING
            soundEngine.isPaused = false
        }
    }

    fun restartRun() {
        soundEngine.playSfx(SfxType.CLICK)
        val mission = _activeMission.value
        if (mission != null) {
            startMission(mission)
        } else {
            startFreeDrive(_progress.value.selectedMap)
        }
    }

    fun commitRunRewardsIfNeeded(world: PhysicsWorld) {
        if (runRewardsCommitted) return
        runRewardsCommitted = true
        val mission = world.mission
        val missionWon = world.phase == RunPhase.MISSION_COMPLETE && mission != null
        val missionBonus = if (missionWon) mission.rewardCoins else 0
        val totalEarned = world.runCoins + missionBonus

        updateAndPersist { cur ->
            val prevBest = cur.bestDistances[world.map.id] ?: 0
            val newBests = cur.bestDistances.toMutableMap()
            if (world.maxDistanceMeters > prevBest) {
                newBests[world.map.id] = world.maxDistanceMeters
            }
            val newCompleted = if (missionWon) {
                cur.completedMissions + mission.id
            } else cur.completedMissions

            cur.copy(
                coins = cur.coins + totalEarned,
                bestDistances = newBests,
                completedMissions = newCompleted
            )
        }
    }

    fun onAppPause() {
        pauseGameplay()
        soundEngine.isPaused = true
    }

    fun onAppResume() {
        if (_screen.value != GameScreen.GAMEPLAY || _activeWorld.value?.phase == RunPhase.RUNNING) {
            soundEngine.isPaused = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.stop()
    }
}
