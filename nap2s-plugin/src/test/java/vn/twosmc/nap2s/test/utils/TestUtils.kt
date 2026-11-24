package vn.twosmc.nap2s.test.utils

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import vn.twosmc.nap2s.Nap2S
import java.io.File
import java.io.FileInputStream

fun Any.setInternal(fieldName: String, value: Any) {
    val clazz = this.javaClass
    val field = runCatching { clazz.getDeclaredField(fieldName) }.getOrNull()
        ?: clazz.superclass.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(this, value)
}

fun <T> setStatic(type: Class<T>, fieldName: String, value: T) {
    val field = type.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(null, value)
}

fun <T> setInstance(type: Class<T>, instance: T) = setStatic(type, "instance", instance)

fun setNap2SInstance(instance: Nap2S) = setInstance(Nap2S::class.java, instance)

fun mockNap2S() = setNap2SInstance(mockk {
    every { server } returns mockk {
        every { pluginManager } returns mockk {
            every { disablePlugin(any()) } just runs
        }
    }

    every { dataFolder } returns File("../testdir")

    every { getResource(any<String>()) } answers {
        runCatching { FileInputStream(File("src/main/resources/${firstArg<String>()}")) }.getOrNull()
    }

    every { logger } returns mockk {
        every { info(any<String>()) } answers { println("LOG: ${firstArg<String>()}") }
        every { log(any(), any<String>(), any<Exception>()) } answers {
            thirdArg<Exception>().printStackTrace()
        }
    }
})