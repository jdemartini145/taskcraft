package pe.aphid.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pe.aphid.core.model.AcidInfo
import pe.aphid.core.model.Element
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.ValueRange

val DbJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

class Converters {
    private val elementMap = MapSerializer(Element.serializer(), Double.serializer())
    private val stageDays = MapSerializer(GrowthStage.serializer(), Int.serializer().nullable)

    @TypeConverter fun elementMapToJson(v: Map<Element, Double>): String = DbJson.encodeToString(elementMap, v)

    @TypeConverter fun jsonToElementMap(s: String): Map<Element, Double> = DbJson.decodeFromString(elementMap, s)

    @TypeConverter fun stageDaysToJson(v: Map<GrowthStage, Int?>): String = DbJson.encodeToString(stageDays, v)

    @TypeConverter fun jsonToStageDays(s: String): Map<GrowthStage, Int?> = DbJson.decodeFromString(stageDays, s)

    @TypeConverter fun rangeToJson(v: ValueRange): String = DbJson.encodeToString(ValueRange.serializer(), v)

    @TypeConverter fun jsonToRange(s: String): ValueRange = DbJson.decodeFromString(ValueRange.serializer(), s)

    @TypeConverter fun acidToJson(v: AcidInfo?): String? = v?.let { DbJson.encodeToString(AcidInfo.serializer(), it) }

    @TypeConverter fun jsonToAcid(s: String?): AcidInfo? = s?.let { DbJson.decodeFromString(AcidInfo.serializer(), it) }
}

private val nullableTargets = MapSerializer(Element.serializer(), Double.serializer().nullable)

fun encodeTargets(v: Map<Element, Double?>): String = DbJson.encodeToString(nullableTargets, v)

fun decodeTargets(s: String): Map<Element, Double?> = DbJson.decodeFromString(nullableTargets, s)
