package shibafu.yukari.export

import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

class ConfigFileUtilityTest {
    companion object {
        @BeforeClass @JvmStatic fun beforeClass() {
            // ConfigFileUtility.filtersにテスト用のマイグレータを登録
            val filters = ConfigFileUtility::class.java.getDeclaredField("filters")
            filters.isAccessible = true
            filters.set(ConfigFileUtility, mapOf<Class<*>, ConfigFileMigrator<*>>(
                    ConfigTestEntity::class.java to TestMigrator()
            ))
        }
    }

    @Test fun exportToJsonTest() {
        val entity = ConfigTestEntity("abcde", 114514, 1919810, "114514")
        val json = ConfigFileUtility.exportToJson(ConfigTestEntity::class.java, listOf(entity.toMap()))
        assertEquals("""{"version":2,"ConfigTestEntity":[{"str":"abcde","num":114514,"num2":1919810,"numstr":"114514"}]}""", json)
    }

    @Test fun importFromJsonTest() {
        val json = """{"version":2,"ConfigTestEntity":[{"str":"abcde","num":114514,"num2":1919810,"numstr":"114514"}]}"""
        val records = ConfigFileUtility.importFromJson(ConfigTestEntity::class.java, json)
        assertEquals(1, records.size)
        assertEquals("abcde", records.first()["str"])
        assertEquals(114514.0, records.first()["num"])
        assertEquals(1919810.0, records.first()["num2"])
        assertEquals("114514", records.first()["numstr"])
    }

    @Test fun importFromJsonMigrateTest() {
        val json = """{"version":1,"ConfigTestEntity":[{"strold":"abcde","num":114514}]}"""
        val records = ConfigFileUtility.importFromJson(ConfigTestEntity::class.java, json)
        assertEquals(1, records.size)
        assertEquals("abcde", records.first()["str"])
        assertEquals(114514.0, records.first()["num"])
        assertEquals(114514.0, records.first()["num2"])
        assertEquals("114514", records.first()["numstr"])
    }
}

/**
 * コンフィグマイグレーションのテスト用エンティティ
 */
data class ConfigTestEntity(var str: String, var num: Int, var num2: Int, var numstr: String) {
    fun toMap(): Map<String, Any> {
        return mapOf("str" to str, "num" to num, "num2" to num2, "numstr" to numstr)
    }
}

/**
 * コンフィグマイグレーションのテスト用マイグレータ
 */
class TestMigrator : ConfigFileMigrator<ConfigTestEntity> {
    override val latestVersion: Int = 2

    constructor() : super(ConfigTestEntity::class.java, {
        // version 1 -> 2
        version(2) {
            it["num2"] = it["num"]
            it["str"] = it["strold"]
            it["numstr"] = (it["num"] as Number).toInt().toString()
        }
        // version 2 -> 3
        version(3) {
            it.remove("str")
            it.remove("num2")
        }
    })
}