package com.iospring.launcher.data

import com.iospring.launcher.model.HomeItem

/**
 * Grid occupancy & iOS 18 Free-Placement solver.
 * Pure Kotlin (no Android dependencies) so it is thoroughly unit-tested.
 */
class GridOccupancy(
    val cols: Int,
    val rows: Int
) {
    private val cells = Array(rows) { arrayOfNulls<String>(cols) }

    fun markItem(item: HomeItem) {
        for (r in item.row until (item.row + item.spanY)) {
            for (c in item.col until (item.col + item.spanX)) {
                if (r in 0 until rows && c in 0 until cols) {
                    cells[r][c] = item.id
                }
            }
        }
    }

    fun canPlace(col: Int, row: Int, spanX: Int, spanY: Int, ignoreItemId: String? = null): Boolean {
        if (col < 0 || row < 0 || col + spanX > cols || row + spanY > rows) return false
        for (r in row until (row + spanY)) {
            for (c in col until (col + spanX)) {
                val occupant = cells[r][c]
                if (occupant != null && occupant != ignoreItemId) {
                    return false
                }
            }
        }
        return true
    }

    fun itemIdAt(col: Int, row: Int): String? {
        if (col !in 0 until cols || row !in 0 until rows) return null
        return cells[row][col]
    }

    fun findFirstVacant(spanX: Int, spanY: Int): Pair<Int, Int>? {
        for (r in 0..(rows - spanY)) {
            for (c in 0..(cols - spanX)) {
                if (canPlace(c, r, spanX, spanY)) {
                    return Pair(c, r)
                }
            }
        }
        return null
    }

    companion object {
        fun forPage(page: Int, cols: Int, rows: Int, items: List<HomeItem>): GridOccupancy {
            val grid = GridOccupancy(cols, rows)
            items.filter { it.page == page }.forEach { grid.markItem(it) }
            return grid
        }
    }
}
