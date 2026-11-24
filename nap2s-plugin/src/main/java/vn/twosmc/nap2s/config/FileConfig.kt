package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.platform.config.FileConfig as PlatformFileConfig

/**
 * FileConfig đặc thù cho Nap2S — extend platform FileConfig.
 * Tự động thay %PREFIX% trong get() và getList().
 */
open class FileConfig(fileName: String) : PlatformFileConfig(Nap2S.instance, fileName) {
    val main = Nap2S.instance

    override fun get(key: String) = super.get(key).replace("%PREFIX%", main.prefix)

    override fun getList(key: String) = super.getList(key).map { it.replace("%PREFIX%", main.prefix) }
}
