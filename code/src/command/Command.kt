package command

interface Command {
    fun execute(context: ICommandContext): Boolean
    fun undo()
}

interface ICommandContext {
    fun error(message: String)
    fun hasErrors(): Boolean
}

class CommandContext() : ICommandContext {
    private val errors = mutableListOf<String>()

    override fun error(message: String) { errors.add(message) }
    override fun hasErrors() = errors.isNotEmpty()
}

abstract class MacroCommand : Command {
    private var currentCommand: Int = 0
    private val commands: MutableList<Command> = mutableListOf()
    private val executedCommands: MutableList<Command> = mutableListOf()

    protected fun add(command: Command) {
        commands.add(command)
    }

    final override fun execute(context: ICommandContext): Boolean {
        for (command in commands.listIterator(currentCommand)) {
            if (!command.execute(context)) return false
            executedCommands.add(0, command)
            currentCommand += 1
        }
        return true
    }

    final override fun undo() {
        for (command in executedCommands) command.undo()
        reset()
    }

    fun restore(states: List<Int>) {
        states.toMutableList().apply {
            _restore(this)
            require(isEmpty()) {
                "state list contained more states than expected: $states"
            }
        }
    }

    private fun _restore(states: MutableList<Int>) {
        require(states.isNotEmpty()) { "state list does not contain enough states" }
        restoreHistory(states.removeAt(0))
        for (command in commands) {
            if (command !is MacroCommand) continue
            command._restore(states)
        }
    }

    private fun restoreHistory(currentIndex: Int) {
        currentCommand = currentIndex
        for (i in 0 until currentIndex) executedCommands.add(0, commands[i])
    }

    fun state(): List<Int> {
        val states = mutableListOf<Int>()
        _state(states)
        return states
    }

    private fun _state(states: MutableList<Int>) {
        states.add(currentCommand)
        for (command in commands) {
            if (command !is MacroCommand) continue
            command._state(states)
        }
    }

    private fun reset() {
        currentCommand = 0
        executedCommands.clear()
    }
}
