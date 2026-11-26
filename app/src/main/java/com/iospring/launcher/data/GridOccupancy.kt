package com.iospring.launcher.data

import com.iospring.launcher.model.HomeItem
import com.iospring.launcher.model.LauncherWorkspaceState

/**
 * Grid occupancy, iOS 18 Free-Placement solver, and Auto-Arrange Compactor.
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
            for (i in items.indices) {
                val item = items[i]
                if (item.page == page) grid.markItem(item)
            }
            return grid
        }

        /**
         * Neatly compacts all Home Screen icons & folders so there are ZERO empty spaces
         * when apps are uninstalled, hidden, or removed.
         *
         * Preserves widgets cleanly in their positions (clamping if grid dimensions shrunk),
         * then flows all 1x1 app shortcuts and folders in reading order (page, row, col)
         * into the earliest vacant cells starting from page 0, and removes trailing/empty pages.
         */
        fun compactWorkspace(state: LauncherWorkspaceState, cols: Int, rows: Int) {
            if (state.items.isEmpty()) {
                state.pagesCount = 1
                return
            }

            val safeCols = cols.coerceAtLeast(1)
            val safeRows = rows.coerceAtLeast(1)

            val widgets = mutableListOf<HomeItem>()
            val flowItems = mutableListOf<HomeItem>()
            for (item in state.items) {
                when (item) {
                    is HomeItem.AppShortcut, is HomeItem.FolderItem -> flowItems.add(item)
                    is HomeItem.BuiltInWidgetItem, is HomeItem.AndroidWidgetItem -> widgets.add(item)
                }
            }

            val readingOrder = compareBy<HomeItem>({ it.page }, { it.row }, { it.col })
            widgets.sortWith(readingOrder)
            flowItems.sortWith(readingOrder)

            val pageGrids = mutableListOf<GridOccupancy>()
            fun gridAt(pageIdx: Int): GridOccupancy {
                while (pageGrids.size <= pageIdx) {
                    pageGrids.add(GridOccupancy(safeCols, safeRows))
                }
                return pageGrids[pageIdx]
            }

            // 1. Place widgets first (preserving original page/slot when possible)
            for (widget in widgets) {
                val targetPage = widget.page.coerceAtLeast(0)
                val maxCol = (safeCols - widget.spanX).coerceAtLeast(0)
                val maxRow = (safeRows - widget.spanY).coerceAtLeast(0)
                val clampedCol = widget.col.coerceIn(0, maxCol)
                val clampedRow = widget.row.coerceIn(0, maxRow)

                val preferredGrid = gridAt(targetPage)
                if (preferredGrid.canPlace(clampedCol, clampedRow, widget.spanX, widget.spanY)) {
                    widget.page = targetPage
                    widget.col = clampedCol
                    widget.row = clampedRow
                    preferredGrid.markItem(widget)
                } else {
                    var placed = false
                    var p = targetPage
                    while (!placed) {
                        val g = gridAt(p)
                        val slot = g.findFirstVacant(widget.spanX, widget.spanY)
                        if (slot != null) {
                            widget.page = p
                            widget.col = slot.first
                            widget.row = slot.second
                            g.markItem(widget)
                            placed = true
                        } else {
                            p++
                        }
                    }
                }
            }

            // 2. Flow all 1x1 app shortcuts and folders seamlessly from Page 0 with zero empty gaps
            var currentPage = 0
            for (item in flowItems) {
                var placed = false
                while (!placed) {
                    val g = gridAt(currentPage)
                    val slot = g.findFirstVacant(1, 1)
                    if (slot != null) {
                        item.page = currentPage
                        item.col = slot.first
                        item.row = slot.second
                        g.markItem(item)
                        placed = true
                    } else {
                        currentPage++
                    }
                }
            }

            // 3. Collapse completely empty intermediate pages so there are never blank pages
            val occupiedPages = state.items.map { it.page }.toSortedSet()
            if (occupiedPages.isNotEmpty()) {
                val pageRemap = HashMap<Int, Int>(occupiedPages.size)
                var newPageIdx = 0
                for (oldPage in occupiedPages) {
                    pageRemap[oldPage] = newPageIdx++
                }
                for (item in state.items) {
                    item.page = pageRemap[item.page] ?: 0
                }
                state.pagesCount = newPageIdx.coerceAtLeast(1)
            } else {
                state.pagesCount = 1
            }
        }
    }
}
