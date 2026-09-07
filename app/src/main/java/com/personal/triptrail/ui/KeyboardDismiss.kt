package com.personal.triptrail.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Observe unhandled taps after children; never consume input or dismiss during scrolling. */
fun Modifier.dismissKeyboardOnBlankTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(focusManager, keyboard) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var handled = down.isConsumed
            var moved = false
            var multiplePointers = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                handled = handled || event.changes.any { it.isConsumed }
                multiplePointers = multiplePointers || event.changes.size > 1
                val pointer = event.changes.firstOrNull { it.id == down.id }
                if (pointer != null && (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            } while (event.changes.any { it.pressed })
            if (!handled && !moved && !multiplePointers) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }
}
