package com.rdr.roast.driver.modbus.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [ModbusCommandExecutor]: parsing and execution of write/wcoil/sleep command strings.
 */
class ModbusCommandExecutorTest {

    private val recorder = mutableListOf<String>()

    private val runner = object : ModbusCommandRunner {
        override fun writeSingle(deviceId: Int, register: Int, value: Int) {
            recorder.add("write($deviceId,$register,$value)")
        }
        override fun writeCoil(deviceId: Int, coil: Int, value: Boolean) {
            recorder.add("wcoil($deviceId,$coil,$value)")
        }
    }

    @Test
    fun `empty string does nothing`() {
        ModbusCommandExecutor.execute(runner, "")
        ModbusCommandExecutor.execute(runner, "   ")
        assertEquals(0, recorder.size)
    }

    @Test
    fun `single write parses and executes`() {
        ModbusCommandExecutor.execute(runner, "write(1, 1008, 2)")
        assertEquals(listOf("write(1,1008,2)"), recorder)
    }

    @Test
    fun `single wcoil with 1 parses as true`() {
        recorder.clear()
        ModbusCommandExecutor.execute(runner, "wcoil(1, 2005, 1)")
        assertEquals(listOf("wcoil(1,2005,true)"), recorder)
    }

    @Test
    fun `single wcoil with 0 parses as false`() {
        recorder.clear()
        ModbusCommandExecutor.execute(runner, "wcoil(1,2006,0)")
        assertEquals(listOf("wcoil(1,2006,false)"), recorder)
    }

    @Test
    fun `multiple commands separated by semicolon execute in order`() {
        recorder.clear()
        ModbusCommandExecutor.execute(runner, "write(1,1008,2);write(1,1008,5)")
        assertEquals(listOf("write(1,1008,2)", "write(1,1008,5)"), recorder)
    }

    @Test
    fun `write and wcoil mixed`() {
        recorder.clear()
        ModbusCommandExecutor.execute(runner, "write(1,1009,2); wcoil(1,2005,1); wcoil(1,2006,0)")
        assertEquals(
            listOf("write(1,1009,2)", "wcoil(1,2005,true)", "wcoil(1,2006,false)"),
            recorder
        )
    }

    @Test
    fun `sleep with decimal is executed`() {
        recorder.clear()
        val start = System.currentTimeMillis()
        ModbusCommandExecutor.execute(runner, "sleep(0.05)")
        val elapsed = System.currentTimeMillis() - start
        assert(elapsed >= 45) { "sleep(0.05) should take at least ~50ms, was $elapsed ms" }
    }

    @Test
    fun `unknown token is skipped and execution continues`() {
        recorder.clear()
        ModbusCommandExecutor.execute(runner, "write(1,1,1); foo(1,2); write(2,2,2)")
        assertEquals(listOf("write(1,1,1)", "write(2,2,2)"), recorder)
    }

    @Test
    fun `case insensitive write and wcoil`() {
        recorder.clear()
        ModbusCommandExecutor.execute(runner, "WRITE(1,10,20); WCOIL(2,30,1)")
        assertEquals(listOf("write(1,10,20)", "wcoil(2,30,true)"), recorder)
    }

    @Test
    fun `runner exception is propagated`() {
        val failingRunner = object : ModbusCommandRunner {
            override fun writeSingle(deviceId: Int, register: Int, value: Int) {
                throw RuntimeException("mock io error")
            }
            override fun writeCoil(deviceId: Int, coil: Int, value: Boolean) {}
        }
        assertThrows<RuntimeException> {
            ModbusCommandExecutor.execute(failingRunner, "write(1,1,1)")
        }
    }
}
