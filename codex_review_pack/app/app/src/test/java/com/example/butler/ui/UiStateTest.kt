package com.example.butler.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UiStateTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel()
    }

    @Test
    fun testAutoExecutableTodoCardAdded() = runBlocking {
        val createTodoJson = """
        {
            "operationId": "op-ui-todo-1",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "買い物に行く",
                "detail": "牛乳とパン",
                "financialImpact": 1,
                "workImpact": 1,
                "mentalLoad": 1
            }
        }
        """.trimIndent()

        viewModel.handleAiJsonInputSuspend(createTodoJson)

        val state = viewModel.uiState.value
        assertEquals(1, state.cards.size)
        assertTrue(state.cards[0] is CardItem.TodoCardItem)

        val todoCard = state.cards[0] as CardItem.TodoCardItem
        assertEquals("買い物に行く", todoCard.title)
        assertEquals("C", todoCard.rankTag)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun testConfirmationCardApprovalAndRejection() = runBlocking {
        val confirmationRequiredJson = """
        {
            "operationId": "op-ui-event-update",
            "actionType": "UPDATE_EVENT",
            "payload": {
                "title": "会議日時変更"
            }
        }
        """.trimIndent()

        viewModel.handleAiJsonInputSuspend(confirmationRequiredJson)

        var state = viewModel.uiState.value
        assertEquals(1, state.cards.size)
        assertTrue(state.cards[0] is CardItem.ConfirmationCardItem)

        val confirmCard = state.cards[0] as CardItem.ConfirmationCardItem
        assertEquals("確認要請: UPDATE_EVENT", confirmCard.title)

        viewModel.rejectConfirmationCard(confirmCard.id)
        state = viewModel.uiState.value
        assertEquals(0, state.cards.size)
        assertFalse(state.canUndo)
    }

    @Test
    fun testUndoRedoStatusSync() = runBlocking {
        val json = """
        {
            "operationId": "op-ui-sync-1",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "同期テスト"
            }
        }
        """.trimIndent()

        viewModel.handleAiJsonInputSuspend(json)
        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)

        assertTrue(viewModel.undo())
        assertFalse(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.canRedo)

        assertTrue(viewModel.redo())
        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)
    }
}
