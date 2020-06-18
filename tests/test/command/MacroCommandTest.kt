package command

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class MacroCommandTest {

    @Test
    fun `executed commands can undo`() {
        val macro = command() + command()
        macro.execute(CommandContext())
        assertCounts(2, 0)
        macro.undo()
        assertCounts(2, 2)
    }

    @Test
    fun `resume commands`() {
        val macro = command() + command()
        macro.restore(listOf(1))
        macro.execute(CommandContext())
        assertCounts(1, 0)
    }

    @Test
    fun `resumed commands can undo`() {
        val macro = command() + command()
        macro.restore(listOf(1))
        macro.execute(CommandContext())
        macro.undo()
        assertCounts(1, 2)
    }

    @Test
    fun `completed commands can undo`() {
        val macro = command() + command()
        macro.restore(listOf(2))
        macro.undo()
        assertCounts(0, 2)
    }

    @Test
    fun `executed in order`() {
        val result = mutableListOf<String>()
        val macro = command { result.add("A") } + command { result.add("B") }
        assertTrue(macro.execute(CommandContext()))
        assertOrder(result, "A", "B")
    }

    @Test
    fun `resumed in order`() {
        val result = mutableListOf<String>()
        val macro = command { result.add("A") } + command { result.add("B") }
        macro.restore(listOf(1))
        assertTrue(macro.execute(CommandContext()))
        assertOrder(result, "B")
    }

    @Test
    fun `undo in reverse order of execution`() {
        val result = mutableListOf<String>()
        val macro = command(undo = { result.add("A"); }) + command(undo = { result.add("B"); })
        macro.execute(CommandContext())
        macro.undo()
        assertOrder(result, "B", "A")
    }

    @Test
    fun `execution stops in case of error`() {
        val macro = command { false } + command()
        assertFalse(macro.execute(CommandContext()))
        assertCounts(1, 0)
    }

    @Test
    fun `resume execution`() {
        var halt = true
        val macro = command { !halt } + command()
        macro.execute(CommandContext())
        assertCounts(1, 0)
        halt = false
        macro.execute(CommandContext())
        assertCounts(3, 0)
    }

    @Test
    fun `macro can have macro commands`() {
        val result = mutableListOf<String>()
        val macro1 = command { result.add("A1") } + command { result.add("B1") }
        val macro2 = TestCommand(listOf(command { result.add("A2") }))
        val macro = macro1 + macro2
        macro.execute(CommandContext())
        assertState(macro, listOf(2, 2, 1))
        assertCounts(3, 0)
        assertOrder(result, "A1", "B1", "A2")
    }

    @Test
    fun `macro can undo`() {
        val result = mutableListOf<String>()
        val macro1 = command(undo = { result.add("A1") }) + command(undo = { result.add("B1") })
        val macro2 = TestCommand(listOf(command(undo = { result.add("A2") })))
        val macro = macro1 + macro2
        macro.restore(listOf(2, 2, 1))
        macro.undo()
        assertCounts(0, 3)
        assertOrder(result, "A2", "B1", "A1")
    }

    @Test
    fun `macro can resume macro commands`() {
        val result = mutableListOf<String>()
        var halt = true
        val macro1 = command { result.add("A1") } + command { result.add("B1"); !halt }
        val macro2 = TestCommand(listOf(command { result.add("A2") }))
        val macro = macro1 + macro2
        macro.execute(CommandContext())
        assertCounts(2, 0)
        assertState(macro, listOf(0, 1, 0))
        halt = false
        macro.execute(CommandContext())
        assertCounts(4, 0)
        assertOrder(result, "A1", "B1", "B1", "A2")
    }

    private var executeCounts = 0
    private var undoCounts = 0

    @BeforeEach
    fun reset() {
        executeCounts = 0
        undoCounts = 0
    }

    private fun assertCounts(expectedExecuteCounts: Int, expectedUndoCounts: Int) {
        assertEquals(expectedExecuteCounts, executeCounts)
        assertEquals(expectedUndoCounts, undoCounts)
    }

    private fun assertOrder(actual: List<String>, vararg expected: String) {
        assertEquals(expected.toList(), actual)
    }

    private fun assertState(macro: MacroCommand, expected: List<Int>) {
        assertEquals(expected, macro.state())
    }

    private fun command(undo: () -> Unit = {}, cmd: (ICommandContext) -> Boolean = { true }) = object : Command {
        override fun execute(context: ICommandContext): Boolean {
            executeCounts++
            return cmd(context)
        }

        override fun undo() {
            undoCounts++
            undo()
        }
    }

    private operator fun Command.plus(other: Command): MacroCommand {
        return TestCommand(listOf(this, other))
    }

    private class TestCommand(commands: List<Command>) : MacroCommand() {
        init {
            commands.forEach(::add)
        }

        fun addCommand(cmd: Command) {
            add(cmd)
        }
    }
}
