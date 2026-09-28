package com.example.butler.domain.logic

import com.example.butler.data.remote.PersonaType
import com.example.butler.domain.model.OperationHistory

class ChangePersonaCommand(
    override val history: OperationHistory,
    val newPersona: PersonaType,
    val previousPersona: PersonaType,
    private val onPersonaChanged: (PersonaType) -> Unit
) : Command {

    override suspend fun execute(): Boolean {
        onPersonaChanged(newPersona)
        return true
    }

    override suspend fun undo(): Boolean {
        onPersonaChanged(previousPersona)
        return true
    }

    override suspend fun redo(): Boolean {
        onPersonaChanged(newPersona)
        return true
    }
}
