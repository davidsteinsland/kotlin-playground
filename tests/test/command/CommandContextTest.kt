package command

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class CommandContextTest {

    @Test
    fun `register errors`() {
        val context = CommandContext()
        command { context.error("An error message") }
            .execute(context)
        assertTrue(context.hasErrors())
    }

    private fun command(cmd: (context: ICommandContext) -> Unit) = object : Command {
        override fun execute(context: ICommandContext): Boolean {
            cmd(context)
            return true
        }

        override fun undo() {}
    }
}
