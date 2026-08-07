package com.phonesync.app.ui.archive

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionReducerTest {

    @Test
    fun toggle_addsWhenAbsent() {
        assertEquals(setOf("a", "b"), SelectionReducer.toggle(setOf("a"), "b"))
    }

    @Test
    fun toggle_removesWhenPresent() {
        assertEquals(setOf("a"), SelectionReducer.toggle(setOf("a", "b"), "b"))
    }

    @Test
    fun toggleAll_selectsEveryIdWhenNoneOrPartiallySelected() {
        assertEquals(setOf("a", "b", "c"), SelectionReducer.toggleAll(emptySet(), listOf("a", "b", "c")))
        assertEquals(setOf("a", "b", "c"), SelectionReducer.toggleAll(setOf("a"), listOf("a", "b", "c")))
    }

    @Test
    fun toggleAll_clearsWhenAllSelected() {
        assertEquals(emptySet<String>(), SelectionReducer.toggleAll(setOf("a", "b", "c"), listOf("a", "b", "c")))
    }

    @Test
    fun toggleAll_emptyList_isNoOp() {
        assertEquals(emptySet<String>(), SelectionReducer.toggleAll(emptySet(), emptyList()))
    }
}
