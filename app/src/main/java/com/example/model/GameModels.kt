package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

enum class GameScreen {
    MAIN_MENU,
    GARAGE,
    MAP_SELECT,
    GAMEPLAY
}

enum class TractorModel(
    val id: String,
    val displayName: String,
    val tagline: String,
    val unlockCost: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val rimColor: Color,
    val basePower: Float,       // Horsepower drive force
    val baseTorque: Float,      // Low-end hill climbing multiplier
    val baseMaxSpeed: Float,    // m/s top speed
    val baseGrip: Float,        // Tyre traction multiplier
    val baseSuspension: Float,  // Spring/damper stability
    val baseFuelTank: Float,    // Max fuel units
    val baseBraking: Float,     // Brake power
    val massKg: Float,
    val wheelBase: Float,       // Distance between rear and front axles (meters)
    val rearWheelRadius: Float, // Meters
    val frontWheelRadius: Float,// Meters
    val cylinderCount: Int,
    val fuelBurnRate: Float
) {
    EICHER_241(
        id = "eicher_241",
        displayName = "Eicher 241",
        tagline = "Air-Cooled Classic • 1-Cyl 24 HP",
        unlockCost = 0,
        primaryColor = Color(0xFFE03A1E),
        secondaryColor = Color(0xFFD8D9DC),
        rimColor = Color(0xFFF0EFEA),
        basePower = 1.00f,
        baseTorque = 1.12f,
        baseMaxSpeed = 8.4f, // ~30 km/h realistic Eicher road speed
        baseGrip = 1.02f,
        baseSuspension = 0.96f,
        baseFuelTank = 100f,
        baseBraking = 1.05f,
        massKg = 1650f,
        wheelBase = 2.05f,
        rearWheelRadius = 0.64f,
        frontWheelRadius = 0.34f,
        cylinderCount = 1,
        fuelBurnRate = 0.85f
    ),
    MAHINDRA_575(
        id = "mahindra_575",
        displayName = "Mahindra 575 DI",
        tagline = "Bhoomiputra Workhorse • 4-Cyl 45 HP",
        unlockCost = 1200,
        primaryColor = Color(0xFFB81D24),
        secondaryColor = Color(0xFF22252A),
        rimColor = Color(0xFFE6E1D5),
        basePower = 1.28f,
        baseTorque = 1.36f,
        baseMaxSpeed = 10.2f, // ~37 km/h realistic Mahindra road speed
        baseGrip = 1.15f,
        baseSuspension = 1.12f,
        baseFuelTank = 120f,
        baseBraking = 1.20f,
        massKg = 2100f,
        wheelBase = 2.22f,
        rearWheelRadius = 0.695f,
        frontWheelRadius = 0.405f,
        cylinderCount = 4,
        fuelBurnRate = 1.00f
    ),
    SONALIKA_750(
        id = "sonalika_750",
        displayName = "Sonalika DI 750",
        tagline = "Heavy Hauler Tiger • 4-Cyl Turbo 55 HP",
        unlockCost = 2600,
        primaryColor = Color(0xFF1958B8),
        secondaryColor = Color(0xFFF3B729),
        rimColor = Color(0xFFF4F6F9),
        basePower = 1.54f,
        baseTorque = 1.60f,
        baseMaxSpeed = 11.8f, // ~42 km/h realistic Sonalika road speed
        baseGrip = 1.28f,
        baseSuspension = 1.25f,
        baseFuelTank = 140f,
        baseBraking = 1.32f,
        massKg = 2480f,
        wheelBase = 2.36f,
        rearWheelRadius = 0.728f,
        frontWheelRadius = 0.483f,
        cylinderCount = 4,
        fuelBurnRate = 1.15f
    )
}

enum class DriverCharacter(
    val id: String,
    val displayName: String,
    val roleTitle: String,
    @param:DrawableRes val faceDrawable: Int,
    val shirtPrimary: Color,
    val shirtAccent: Color,
    val pantsColor: Color,
    val quote: String
) {
    VANSH(
        id = "vansh",
        displayName = "Vansh",
        roleTitle = "Hill Climb Specialist",
        faceDrawable = R.drawable.face_vansh,
        shirtPrimary = Color(0xFFF0EDE4),
        shirtAccent = Color(0xFF3E6B48),
        pantsColor = Color(0xFF2F5580),
        quote = "Full throttle on every ridge!"
    ),
    ANSH(
        id = "ansh",
        displayName = "Ansh",
        roleTitle = "Master Tractor Captain",
        faceDrawable = R.drawable.face_ansh,
        shirtPrimary = Color(0xFFEBE7DC),
        shirtAccent = Color(0xFFC79B42),
        pantsColor = Color(0xFF283442),
        quote = "Heavy load, steady hand, zero fear."
    ),
    AKSHAY(
        id = "akshay",
        displayName = "Akshay",
        roleTitle = "Off-Road Cargo Hauler",
        faceDrawable = R.drawable.face_akshay,
        shirtPrimary = Color(0xFFC5D1B8),
        shirtAccent = Color(0xFF4F5D46),
        pantsColor = Color(0xFF5A6572),
        quote = "Mud, monsoon or mountains — we deliver!"
    )
}

enum class TrailerType(
    val id: String,
    val displayName: String,
    val description: String,
    val unlockCost: Int,
    val bedLength: Float,    // meters
    val bedHeight: Float,    // meters
    val emptyMassKg: Float,
    val wheelRadius: Float,
    val bodyColor: Color,
    val accentColor: Color,
    val capacityPieces: Int
) {
    NONE(
        id = "none",
        displayName = "Solo Tractor",
        description = "No trailer attached • Agile hill climber",
        unlockCost = 0,
        bedLength = 0f,
        bedHeight = 0f,
        emptyMassKg = 0f,
        wheelRadius = 0f,
        bodyColor = Color.Transparent,
        accentColor = Color.Transparent,
        capacityPieces = 0
    ),
    SMALL_TROLLEY(
        id = "small_trolley",
        displayName = "Kisan Trolley",
        description = "Nimble 2-wheel steel farm trolley",
        unlockCost = 0,
        bedLength = 2.6f,
        bedHeight = 0.65f,
        emptyMassKg = 680f,
        wheelRadius = 0.44f,
        bodyColor = Color(0xFF2F6F4E),
        accentColor = Color(0xFFF5A623),
        capacityPieces = 6
    ),
    HEAVY_TIPPER(
        id = "heavy_tipper",
        displayName = "Desi Hydraulic Tipper",
        description = "Reinforced high-side steel cargo bed",
        unlockCost = 650,
        bedLength = 3.2f,
        bedHeight = 0.85f,
        emptyMassKg = 1050f,
        wheelRadius = 0.48f,
        bodyColor = Color(0xFFC85A17),
        accentColor = Color(0xFF23272A),
        capacityPieces = 8
    ),
    TIMBER_CART(
        id = "timber_cart",
        displayName = "Long Hauler Trailer",
        description = "Long heavy-duty log & brick carrier",
        unlockCost = 1100,
        bedLength = 3.8f,
        bedHeight = 0.78f,
        emptyMassKg = 1280f,
        wheelRadius = 0.50f,
        bodyColor = Color(0xFF394E6A),
        accentColor = Color(0xFFFFC857),
        capacityPieces = 10
    )
}

enum class CargoType(
    val id: String,
    val displayName: String,
    val unlockCost: Int,
    val totalMassKg: Float,
    val rewardMultiplier: Float,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    NONE("none", "Empty Bed", 0, 0f, 1.0f, Color.Transparent, Color.Transparent),
    CROPS("crops", "Wheat & Sugarcane", 0, 620f, 1.35f, Color(0xFFE7B844), Color(0xFF4CA64C)),
    SOIL("soil", "Farm Soil", 0, 920f, 1.50f, Color(0xFF6B4423), Color(0xFF4E3118)),
    SAND("sand", "River Sand", 350, 1180f, 1.70f, Color(0xFFDDC185), Color(0xFFC4A463)),
    WOOD("wood", "Teak Logs", 500, 1420f, 1.85f, Color(0xFF8C5A32), Color(0xFF5E391D)),
    BRICKS("bricks", "Kiln Bricks", 750, 1780f, 2.00f, Color(0xFFB8432A), Color(0xFF8E2D18)),
    STONES("stones", "Quarry Boulders", 1000, 2150f, 2.30f, Color(0xFF7D858D), Color(0xFF555B62))
}

enum class SurfaceType(val baseGrip: Float, val rollingDrag: Float) {
    DIRT(0.95f, 0.015f),
    ASPHALT(1.12f, 0.009f),
    MUD(0.58f, 0.036f),
    SAND(0.68f, 0.032f),
    WET(0.74f, 0.020f),
    ROCK(0.88f, 0.022f)
}

enum class MapEnvironment(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val unlockCost: Int,
    val difficultyLabel: String,
    val skyTop: Color,
    val skyBottom: Color,
    val hillFarColor: Color,
    val hillNearColor: Color,
    val roadTopColor: Color,
    val groundFillColor: Color,
    val primarySurface: SurfaceType,
    val hillAmplitude: Float,
    val bumpiness: Float,
    val mudChance: Float,
    val hasRain: Boolean = false,
    val isNightOrDusk: Boolean = false,
    val propTheme: String
) {
    INDIAN_VILLAGE(
        id = "indian_village",
        displayName = "Indian Village",
        subtitle = "Mud huts, neem trees & dirt lanes",
        unlockCost = 0,
        difficultyLabel = "Easy",
        skyTop = Color(0xFF49A6E8),
        skyBottom = Color(0xFFFBE2A8),
        hillFarColor = Color(0xFF6B9473),
        hillNearColor = Color(0xFF4C7A49),
        roadTopColor = Color(0xFFB57B48),
        groundFillColor = Color(0xFF5D3D22),
        primarySurface = SurfaceType.DIRT,
        hillAmplitude = 3.2f,
        bumpiness = 0.55f,
        mudChance = 0.14f,
        propTheme = "village"
    ),
    FARMING_FIELDS(
        id = "farming_fields",
        displayName = "Farming Fields",
        subtitle = "Lush crops, canals & farm bunds",
        unlockCost = 0,
        difficultyLabel = "Easy",
        skyTop = Color(0xFF2D93D1),
        skyBottom = Color(0xFFD6F3C8),
        hillFarColor = Color(0xFF4E8C57),
        hillNearColor = Color(0xFF2F7536),
        roadTopColor = Color(0xFF8C5E35),
        groundFillColor = Color(0xFF432A15),
        primarySurface = SurfaceType.DIRT,
        hillAmplitude = 2.8f,
        bumpiness = 0.65f,
        mudChance = 0.22f,
        propTheme = "farm"
    ),
    INDIAN_BAZAAR(
        id = "indian_bazaar",
        displayName = "Indian Bazaar",
        subtitle = "Market stalls, buntings & speed humps",
        unlockCost = 400,
        difficultyLabel = "Medium",
        skyTop = Color(0xFFFF8F5A),
        skyBottom = Color(0xFFFFDDA1),
        hillFarColor = Color(0xFF8C6264),
        hillNearColor = Color(0xFF66444B),
        roadTopColor = Color(0xFF52565E),
        groundFillColor = Color(0xFF2E3036),
        primarySurface = SurfaceType.ASPHALT,
        hillAmplitude = 2.5f,
        bumpiness = 0.90f,
        mudChance = 0.08f,
        propTheme = "bazaar"
    ),
    HIGHWAY(
        id = "highway",
        displayName = "National Highway",
        subtitle = "Wide bridges, milestones & rolling climbs",
        unlockCost = 650,
        difficultyLabel = "Medium",
        skyTop = Color(0xFF2A79C7),
        skyBottom = Color(0xFFBDE0FE),
        hillFarColor = Color(0xFF5F7A94),
        hillNearColor = Color(0xFF405A6E),
        roadTopColor = Color(0xFF3E4248),
        groundFillColor = Color(0xFF23262B),
        primarySurface = SurfaceType.ASPHALT,
        hillAmplitude = 4.4f,
        bumpiness = 0.35f,
        mudChance = 0.04f,
        propTheme = "highway"
    ),
    FOREST(
        id = "forest",
        displayName = "Teak Forest",
        subtitle = "Dense canopy, roots & slippery mud",
        unlockCost = 900,
        difficultyLabel = "Hard",
        skyTop = Color(0xFF1F4E3E),
        skyBottom = Color(0xFF86B88D),
        hillFarColor = Color(0xFF234A32),
        hillNearColor = Color(0xFF173521),
        roadTopColor = Color(0xFF6B4729),
        groundFillColor = Color(0xFF332113),
        primarySurface = SurfaceType.MUD,
        hillAmplitude = 4.6f,
        bumpiness = 0.95f,
        mudChance = 0.42f,
        propTheme = "forest"
    ),
    DESERT(
        id = "desert",
        displayName = "Thar Desert",
        subtitle = "Golden sand dunes & fort silhouettes",
        unlockCost = 1200,
        difficultyLabel = "Hard",
        skyTop = Color(0xFFE86A2E),
        skyBottom = Color(0xFFFFE194),
        hillFarColor = Color(0xFFD49757),
        hillNearColor = Color(0xFFBD7D3D),
        roadTopColor = Color(0xFFE0B467),
        groundFillColor = Color(0xFF9C6F34),
        primarySurface = SurfaceType.SAND,
        hillAmplitude = 5.4f,
        bumpiness = 0.50f,
        mudChance = 0.0f,
        propTheme = "desert"
    ),
    RAINY_VILLAGE(
        id = "rainy_village",
        displayName = "Rainy Village",
        subtitle = "Monsoon storm, puddles & slick mud",
        unlockCost = 1500,
        difficultyLabel = "Hard",
        skyTop = Color(0xFF1D2C3A),
        skyBottom = Color(0xFF5E7385),
        hillFarColor = Color(0xFF2F424D),
        hillNearColor = Color(0xFF22332C),
        roadTopColor = Color(0xFF564234),
        groundFillColor = Color(0xFF2B2019),
        primarySurface = SurfaceType.WET,
        hillAmplitude = 4.0f,
        bumpiness = 0.85f,
        mudChance = 0.48f,
        hasRain = true,
        isNightOrDusk = true,
        propTheme = "rainy"
    ),
    CONSTRUCTION_AREA(
        id = "construction_area",
        displayName = "Construction Site",
        subtitle = "Gravel mounds, cranes & rough rubble",
        unlockCost = 1800,
        difficultyLabel = "Very Hard",
        skyTop = Color(0xFF56768A),
        skyBottom = Color(0xFFE8C8A4),
        hillFarColor = Color(0xFF6B6E73),
        hillNearColor = Color(0xFF4E5054),
        roadTopColor = Color(0xFF8C7A68),
        groundFillColor = Color(0xFF473D35),
        primarySurface = SurfaceType.ROCK,
        hillAmplitude = 5.2f,
        bumpiness = 1.15f,
        mudChance = 0.18f,
        propTheme = "construction"
    ),
    CITY_OUTSKIRTS(
        id = "city_outskirts",
        displayName = "City Outskirts",
        subtitle = "Apartments, pylons & busy ring road",
        unlockCost = 2100,
        difficultyLabel = "Very Hard",
        skyTop = Color(0xFF2B3554),
        skyBottom = Color(0xFFF29E6B),
        hillFarColor = Color(0xFF3A4159),
        hillNearColor = Color(0xFF272D40),
        roadTopColor = Color(0xFF3B3F46),
        groundFillColor = Color(0xFF1D1F24),
        primarySurface = SurfaceType.ASPHALT,
        hillAmplitude = 4.2f,
        bumpiness = 0.95f,
        mudChance = 0.10f,
        isNightOrDusk = true,
        propTheme = "city"
    ),
    MOUNTAIN_ROADS(
        id = "mountain_roads",
        displayName = "Mountain Roads",
        subtitle = "Steep Himalayan climbs & rocky valleys",
        unlockCost = 2500,
        difficultyLabel = "Extreme",
        skyTop = Color(0xFF194575),
        skyBottom = Color(0xFFCDE6F5),
        hillFarColor = Color(0xFF5A758E),
        hillNearColor = Color(0xFF394D5C),
        roadTopColor = Color(0xFF7D7B76),
        groundFillColor = Color(0xFF42403C),
        primarySurface = SurfaceType.ROCK,
        hillAmplitude = 7.4f,
        bumpiness = 1.05f,
        mudChance = 0.15f,
        propTheme = "mountain"
    )
}

enum class UpgradeType(val id: String, val displayName: String, val shortHint: String, val maxLevel: Int = 5) {
    ENGINE("engine", "Engine Power", "Higher top speed & pull"),
    TORQUE("torque", "Gear Torque", "Stronger steep hill climb"),
    TYRES("tyres", "Lug Tyres", "Better mud & sand traction"),
    SUSPENSION("suspension", "Suspension", "Smoother bump absorption"),
    FUEL("fuel", "Diesel Tank", "Larger fuel capacity"),
    BRAKING("braking", "Hydraulic Brakes", "Faster stopping on slopes");

    fun upgradeCost(currentLevel: Int): Int = 180 * currentLevel * currentLevel
}

data class MissionDef(
    val id: String,
    val title: String,
    val description: String,
    val map: MapEnvironment,
    val requiredCargo: CargoType,
    val targetDistanceMeters: Int,
    val timeLimitSeconds: Int?, // null = untimed
    val rewardCoins: Int
)

object GameCatalog {
    val missions: List<MissionDef> = listOf(
        MissionDef(
            id = "m_village_soil",
            title = "Village Bund Repair",
            description = "Transport Farm Soil 350m through Indian Village.",
            map = MapEnvironment.INDIAN_VILLAGE,
            requiredCargo = CargoType.SOIL,
            targetDistanceMeters = 350,
            timeLimitSeconds = null,
            rewardCoins = 350
        ),
        MissionDef(
            id = "m_farm_crops",
            title = "Mandi Harvest Run",
            description = "Deliver Wheat & Sugarcane 420m across Farming Fields.",
            map = MapEnvironment.FARMING_FIELDS,
            requiredCargo = CargoType.CROPS,
            targetDistanceMeters = 420,
            timeLimitSeconds = 75,
            rewardCoins = 450
        ),
        MissionDef(
            id = "m_bazaar_bricks",
            title = "Bazaar Brick Rush",
            description = "Carry Kiln Bricks 450m in Indian Bazaar before the timer runs out!",
            map = MapEnvironment.INDIAN_BAZAAR,
            requiredCargo = CargoType.BRICKS,
            targetDistanceMeters = 450,
            timeLimitSeconds = 70,
            rewardCoins = 600
        ),
        MissionDef(
            id = "m_highway_express",
            title = "NH Freight Haul",
            description = "Transport River Sand 600m on National Highway.",
            map = MapEnvironment.HIGHWAY,
            requiredCargo = CargoType.SAND,
            targetDistanceMeters = 600,
            timeLimitSeconds = 80,
            rewardCoins = 680
        ),
        MissionDef(
            id = "m_forest_timber",
            title = "Teak Timber Trail",
            description = "Haul Teak Logs 420m through muddy Teak Forest.",
            map = MapEnvironment.FOREST,
            requiredCargo = CargoType.WOOD,
            targetDistanceMeters = 420,
            timeLimitSeconds = null,
            rewardCoins = 750
        ),
        MissionDef(
            id = "m_desert_dunes",
            title = "Thar Sand Convoy",
            description = "Deliver Soil 480m across shifting Thar Desert dunes.",
            map = MapEnvironment.DESERT,
            requiredCargo = CargoType.SOIL,
            targetDistanceMeters = 480,
            timeLimitSeconds = null,
            rewardCoins = 820
        ),
        MissionDef(
            id = "m_rainy_rescue",
            title = "Monsoon Grain Run",
            description = "Carry Wheat & Sugarcane 450m in Rainy Village without flipping!",
            map = MapEnvironment.RAINY_VILLAGE,
            requiredCargo = CargoType.CROPS,
            targetDistanceMeters = 450,
            timeLimitSeconds = null,
            rewardCoins = 950
        ),
        MissionDef(
            id = "m_construction_stones",
            title = "Flyover Quarry Load",
            description = "Haul heavy Quarry Boulders 450m across Construction Site.",
            map = MapEnvironment.CONSTRUCTION_AREA,
            requiredCargo = CargoType.STONES,
            targetDistanceMeters = 450,
            timeLimitSeconds = null,
            rewardCoins = 1100
        ),
        MissionDef(
            id = "m_city_night",
            title = "Ring Road Delivery",
            description = "Rush Kiln Bricks 550m through City Outskirts in 85s.",
            map = MapEnvironment.CITY_OUTSKIRTS,
            requiredCargo = CargoType.BRICKS,
            targetDistanceMeters = 550,
            timeLimitSeconds = 85,
            rewardCoins = 1250
        ),
        MissionDef(
            id = "m_mountain_king",
            title = "Himalayan Ridge King",
            description = "Conquer Mountain Roads for 500m hauling Quarry Boulders!",
            map = MapEnvironment.MOUNTAIN_ROADS,
            requiredCargo = CargoType.STONES,
            targetDistanceMeters = 500,
            timeLimitSeconds = null,
            rewardCoins = 1600
        )
    )
}

data class TractorUpgrades(
    val levels: Map<UpgradeType, Int> = UpgradeType.entries.associateWith { 1 }
) {
    fun levelOf(type: UpgradeType): Int = levels[type] ?: 1
}

data class PlayerProgress(
    val coins: Int = 800,
    val selectedTractor: TractorModel = TractorModel.EICHER_241,
    val selectedDriver: DriverCharacter = DriverCharacter.VANSH,
    val selectedTrailer: TrailerType = TrailerType.SMALL_TROLLEY,
    val selectedCargo: CargoType = CargoType.CROPS,
    val selectedMap: MapEnvironment = MapEnvironment.INDIAN_VILLAGE,
    val unlockedTractors: Set<String> = setOf(TractorModel.EICHER_241.id),
    val unlockedTrailers: Set<String> = setOf(TrailerType.NONE.id, TrailerType.SMALL_TROLLEY.id),
    val unlockedCargos: Set<String> = setOf(CargoType.NONE.id, CargoType.CROPS.id, CargoType.SOIL.id),
    val unlockedMaps: Set<String> = setOf(MapEnvironment.INDIAN_VILLAGE.id, MapEnvironment.FARMING_FIELDS.id),
    val tractorUpgrades: Map<String, TractorUpgrades> = TractorModel.entries.associate { it.id to TractorUpgrades() },
    val completedMissions: Set<String> = emptySet(),
    val bestDistances: Map<String, Int> = emptyMap(),
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true
) {
    fun upgradesFor(tractor: TractorModel): TractorUpgrades =
        tractorUpgrades[tractor.id] ?: TractorUpgrades()
}
