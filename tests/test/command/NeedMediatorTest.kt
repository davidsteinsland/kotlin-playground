package command

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NeedMediatorTest {

    private val NAME = "Hello, World"
    private val ADDRESS = "1337 Computer Road"
    private val FNR = "fnr"

    private val dao = PersonDao()

    @Test
    fun `execute and stop`() {
        // a new need is created after received
        // on the message bus
        val need = createNewNeed()
        // execute stops because
        // some command needs more information
        assertFalse(need.execute(CommandContext()))
    }

    @Test
    fun `restore and resume`() {
        // personalia is received on the message bus,
        // the need is restored and execution continues
        val personalia = Personalia(NAME, ADDRESS)
        val need = restoreNeedFromDB()
        // update need more information
        need.personalia(personalia)
        assertTrue(need.execute(CommandContext()))
    }

    private fun createNewNeed() = PaymentNeed(dao, FNR)

    private fun restoreNeedFromDB(): PaymentNeed {
        val need = createNewNeed()
        need.restore(listOf(1))
        return need
    }

    private class PaymentNeed(personDao: PersonDao, fnr: String) : MacroCommand() {
        private val createPersonCmd = CreatePersonCommand(personDao, fnr)
            .also { add(it) }
        private val personaliaCmd = FetchPersonaliaCommand(personDao)
            .also { add(it) }

        fun personalia(personalia: Personalia) {
            personaliaCmd.personalia(personalia)
        }
    }

    private class CreatePersonCommand(
        private val personDao: PersonDao,
        private val fnr: String
    ): Command {

        override fun execute(context: ICommandContext): Boolean {
            return personDao.insertPerson(fnr)
        }

        override fun undo() {
            personDao.deletePerson(fnr)
        }
    }

    private class FetchPersonaliaCommand(private val personDao: PersonDao) : Command {
        private var personalia: Personalia? = null

        fun personalia(personalia: Personalia) {
            this.personalia = personalia
        }

        override fun execute(context: ICommandContext): Boolean {
            val info = personalia ?: return false
            info.updatePersonalia(personDao)
            return true
        }

        override fun undo() {}
    }

    private class Personalia(private val name: String,
                             private val address: String) {
        fun updatePersonalia(dao: PersonDao) {
            dao.updatePersonalia(name, address)
        }
    }

    private class PersonDao {
        fun insertPerson(fnr: String): Boolean { return true }
        fun deletePerson(fnr: String) {}
        fun updatePersonalia(name: String, address: String) {}
    }
}
