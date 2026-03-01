package com.rdr.roast.ui

import javafx.event.EventHandler
import javafx.scene.Cursor
import javafx.scene.Scene
import javafx.scene.input.MouseEvent
import javafx.stage.Stage

/**
 * Attaches mouse handlers to a scene (or root node) to allow resizing an undecorated stage
 * by dragging edges and corners. Hit area is [RESIZE_MARGIN] px.
 */
object ResizeHelper {

    const val RESIZE_MARGIN = 4

    fun enableResize(scene: Scene, stage: Stage) {
        val root = scene.root ?: return
        root.addEventFilter(MouseEvent.MOUSE_MOVED) { e ->
            if (stage.isMaximized) return@addEventFilter
            val edge = edgeAt(e.x, e.y, scene.width, scene.height)
            root.cursor = when (edge) {
                Edge.N -> Cursor.N_RESIZE
                Edge.S -> Cursor.S_RESIZE
                Edge.E -> Cursor.E_RESIZE
                Edge.W -> Cursor.W_RESIZE
                Edge.NE -> Cursor.NE_RESIZE
                Edge.NW -> Cursor.NW_RESIZE
                Edge.SE -> Cursor.SE_RESIZE
                Edge.SW -> Cursor.SW_RESIZE
                Edge.NONE -> Cursor.DEFAULT
            }
        }
        root.addEventFilter(MouseEvent.MOUSE_PRESSED) { e ->
            if (stage.isMaximized) return@addEventFilter
            val edge = edgeAt(e.x, e.y, scene.width, scene.height)
            if (edge != Edge.NONE) {
                e.consume()
                startResize(stage, edge, e.screenX, e.screenY)
            }
        }
    }

    private enum class Edge { N, S, E, W, NE, NW, SE, SW, NONE }

    private fun edgeAt(x: Double, y: Double, width: Double, height: Double): Edge {
        val m = RESIZE_MARGIN.toDouble()
        val top = y < m
        val bottom = y >= height - m
        val left = x < m
        val right = x >= width - m
        return when {
            top && left -> Edge.NW
            top && right -> Edge.NE
            bottom && left -> Edge.SW
            bottom && right -> Edge.SE
            top -> Edge.N
            bottom -> Edge.S
            left -> Edge.W
            right -> Edge.E
            else -> Edge.NONE
        }
    }

    private fun startResize(stage: Stage, edge: Edge, screenX: Double, screenY: Double) {
        val x0 = screenX
        val y0 = screenY
        val w0 = stage.width
        val h0 = stage.height
        val layoutX0 = stage.x
        val layoutY0 = stage.y
        val root = stage.scene?.root ?: return
        val scene = stage.scene ?: return

        val dragHandler: (MouseEvent) -> Unit = { e ->
            val dx = e.screenX - x0
            val dy = e.screenY - y0
            var newX = layoutX0
            var newY = layoutY0
            var newW = w0
            var newH = h0
            when (edge) {
                Edge.E -> newW = (w0 + dx).coerceAtLeast(stage.minWidth)
                Edge.W -> {
                    newW = (w0 - dx).coerceAtLeast(stage.minWidth)
                    if (newW > 0) newX = layoutX0 + w0 - newW
                }
                Edge.S -> newH = (h0 + dy).coerceAtLeast(stage.minHeight)
                Edge.N -> {
                    newH = (h0 - dy).coerceAtLeast(stage.minHeight)
                    if (newH > 0) newY = layoutY0 + h0 - newH
                }
                Edge.SE -> {
                    newW = (w0 + dx).coerceAtLeast(stage.minWidth)
                    newH = (h0 + dy).coerceAtLeast(stage.minHeight)
                }
                Edge.SW -> {
                    newW = (w0 - dx).coerceAtLeast(stage.minWidth)
                    newH = (h0 + dy).coerceAtLeast(stage.minHeight)
                    if (newW > 0) newX = layoutX0 + w0 - newW
                }
                Edge.NE -> {
                    newW = (w0 + dx).coerceAtLeast(stage.minWidth)
                    newH = (h0 - dy).coerceAtLeast(stage.minHeight)
                    if (newH > 0) newY = layoutY0 + h0 - newH
                }
                Edge.NW -> {
                    newW = (w0 - dx).coerceAtLeast(stage.minWidth)
                    newH = (h0 - dy).coerceAtLeast(stage.minHeight)
                    if (newW > 0) newX = layoutX0 + w0 - newW
                    if (newH > 0) newY = layoutY0 + h0 - newH
                }
                Edge.NONE -> {}
            }
            stage.x = newX
            stage.y = newY
            stage.width = newW
            stage.height = newH
        }

        val releaseHandler = object : EventHandler<MouseEvent> {
            override fun handle(e: MouseEvent) {
                scene.removeEventHandler(MouseEvent.MOUSE_DRAGGED, dragHandler)
                scene.removeEventHandler(MouseEvent.MOUSE_RELEASED, this)
                root.cursor = Cursor.DEFAULT
            }
        }

        scene.addEventHandler(MouseEvent.MOUSE_DRAGGED, dragHandler)
        scene.addEventHandler(MouseEvent.MOUSE_RELEASED, releaseHandler)
    }
}
