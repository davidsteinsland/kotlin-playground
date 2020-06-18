package command

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class MacroCommandTest {

    @Test
    fun `executed in order`() {
        val result = mutableListOf<String>()
        val macro = command { result.add("A"); true } + command { result.add("B"); true }
        assertTrue(macro.execute(CommandContext()))
        assertEquals("A", result[0])
        assertEquals("B", result[1])
    }

    @Test
    fun `execution stops in case of error`() {
        val result = mutableListOf<String>()
        val macro = command { result.add("A"); false } + command { result.add("B"); true }
        assertFalse(macro.execute(CommandContext()))
        assertEquals(1, result.size)
        assertEquals("A", result[0])
    }

    @Test
    fun `undo in reverse order of execution`() {
        val result = mutableListOf<String>()
        val macro = command(undo = { result.add("A"); }) + command(undo = { result.add("B"); })
        macro.execute(CommandContext())
        macro.undo()
        assertEquals("B", result[0])
        assertEquals("A", result[1])
    }

    @Test
    fun `resume execution`() {
        val result = mutableListOf<String>()
        var halt = true
        val macro = command { result.add("A"); !halt } + command { result.add("B"); true }
        macro.execute(CommandContext())
        assertEquals(1, result.size)
        assertEquals("A", result[0])
        halt = false
        macro.execute(CommandContext())
        assertEquals("A", result[0])
        assertEquals("A", result[1])
        assertEquals("B", result[2])
    }

    @Test
    fun `resume execution after instantiation`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(listOf(command { result.add("A"); true }, command { result.add("B"); true }))
        macro.restore(listOf(1))
        macro.execute(CommandContext())
        assertEquals("B", result[0])
    }

    @Test
    fun `undo after reinstantiation when commands are added after init`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(emptyList())
        macro.addCommand(command(undo = { result.add("A") }))
        macro.addCommand(command(undo = { result.add("B") }))
        macro.restore(listOf(2))
        macro.undo()
        assertEquals("B", result[0])
        assertEquals("A", result[1])
    }

    @Test
    fun `resume then undo`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(listOf(command(undo = { result.add("A") }), command(undo = { result.add("B") })))
        macro.restore(listOf(1))
        macro.execute(CommandContext())
        macro.undo()
        assertEquals("B", result[0])
        assertEquals("A", result[1])
    }

    @Test
    fun `undo after reinstantiation`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(listOf(command(undo = { result.add("A") }), command(undo = { result.add("B") })))
        macro.restore(listOf(2))
        macro.undo()
        assertEquals("B", result[0])
        assertEquals("A", result[1])
    }

    @Test
    fun `macro can have macro commands`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(listOf(
            TestCommand(listOf(command { result.add("A1"); true }, command { result.add("B1"); true })),
            TestCommand(listOf(command { result.add("A2"); true }))
        ))
        macro.execute(CommandContext())
        assertEquals("A1", result[0])
        assertEquals("B1", result[1])
        assertEquals("A2", result[2])
    }

    @Test
    fun `macro can resume macro commands`() {
        val result = mutableListOf<String>()
        var halt = true
        val macro = TestCommand(listOf(
            TestCommand(listOf(command { result.add("A1"); true }, command { result.add("B1"); !halt })),
            TestCommand(listOf(command { result.add("A2"); true }))
        ))
        macro.execute(CommandContext())
        assertEquals(2, result.size)
        halt = false
        macro.execute(CommandContext())
        assertEquals("A1", result[0])
        assertEquals("B1", result[1])
        assertEquals("B1", result[2])
        assertEquals("A2", result[3])
    }

    @Test
    fun `macro states`() {
        val result = mutableListOf<String>()
        var halt = true
        val macro = TestCommand(listOf(
            TestCommand(listOf(command { result.add("A1"); true }, command { result.add("B1"); !halt })),
            TestCommand(listOf(command { result.add("A2"); true }))
        ))
        macro.execute(CommandContext())
        assertEquals(listOf(0, 1, 0), macro.state())
        halt = false
        macro.execute(CommandContext())
        assertEquals(listOf(2, 2, 1), macro.state())
    }

    @Test
    fun `resume macro after restore`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(listOf(
            TestCommand(listOf(command { result.add("A1"); true }, command { result.add("B1"); false })),
            TestCommand(listOf(command { result.add("A2"); true }))
        ))
        macro.restore(listOf(1, 2, 0))
        macro.execute(CommandContext())
        assertEquals("A2", result[0])
        assertEquals(listOf(2, 2, 1), macro.state())
    }

    @Test
    fun `undo macro with macros after execute`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(listOf(
            TestCommand(listOf(command(undo = { result.add("A1") }), command(undo = { result.add("B1") }))),
            TestCommand(listOf(command(undo = { result.add("A2") })))
        ))
        macro.execute(CommandContext())
        macro.undo()
        assertEquals("A2", result[0])
        assertEquals("B1", result[1])
        assertEquals("A1", result[2])
    }

    @Test
    fun `undo macro after restore`() {
        val result = mutableListOf<String>()
        val macro = TestCommand(listOf(
            TestCommand(listOf(command(undo = { result.add("A1") }), command(undo = { result.add("B1") }))),
            TestCommand(listOf(command(undo = { result.add("A2") })))
        ))
        macro.restore(listOf(2, 2, 1))
        macro.undo()
        assertEquals("A2", result[0])
        assertEquals("B1", result[1])
        assertEquals("A1", result[2])
    }

    private fun command(undo: () -> Unit = {}, cmd: (ICommandContext) -> Boolean = { true }) = object : Command {
        override fun execute(context: ICommandContext): Boolean {
            return cmd(context)
        }

        override fun undo() {
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
