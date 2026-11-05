package io.github.m1omg.shrink.desktop.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays out equally wide cells in as many columns as fit. Only the given column
 * counts are used, e.g. {4, 2, 1} so four cards never end up as 3 + 1.
 */
public final class ResponsiveGrid implements LayoutManager {

    private final int minCellWidth;
    private final int[] columnChoices;
    private final int gap;

    public ResponsiveGrid(int minCellWidth, int gap, int... columnChoices) {
        this.minCellWidth = minCellWidth;
        this.gap = gap;
        this.columnChoices = columnChoices.length == 0 ? new int[] {1} : columnChoices.clone();
    }

    int columnsFor(int width, int count) {
        for (int c : columnChoices) {
            int cols = Math.min(c, Math.max(1, count));
            if (width <= 0 || cols * minCellWidth + (cols - 1) * gap <= width) {
                return cols;
            }
        }
        return 1;
    }

    private List<Component> visible(Container parent) {
        List<Component> list = new ArrayList<>();
        for (Component c : parent.getComponents()) {
            if (c.isVisible()) {
                list.add(c);
            }
        }
        return list;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        List<Component> cells = visible(parent);
        int width = parent.getWidth() - in.left - in.right;
        int cols = columnsFor(width, cells.size());
        int cellWidth = width > 0 ? (width - (cols - 1) * gap) / cols : minCellWidth;
        int height = 0;
        for (int row = 0; row * cols < cells.size(); row++) {
            int rowHeight = 0;
            for (int i = row * cols; i < Math.min(cells.size(), (row + 1) * cols); i++) {
                rowHeight = Math.max(rowHeight, StackLayout.heightFor(cells.get(i), cellWidth));
            }
            height += rowHeight + (row > 0 ? gap : 0);
        }
        int prefWidth = cols * minCellWidth + (cols - 1) * gap;
        return new Dimension(prefWidth + in.left + in.right, height + in.top + in.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        return new Dimension(minCellWidth + in.left + in.right, in.top + in.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        List<Component> cells = visible(parent);
        int width = parent.getWidth() - in.left - in.right;
        int cols = columnsFor(width, cells.size());
        int cellWidth = (width - (cols - 1) * gap) / cols;
        int y = in.top;
        for (int row = 0; row * cols < cells.size(); row++) {
            int rowHeight = 0;
            int end = Math.min(cells.size(), (row + 1) * cols);
            for (int i = row * cols; i < end; i++) {
                rowHeight = Math.max(rowHeight, StackLayout.heightFor(cells.get(i), cellWidth));
            }
            for (int i = row * cols; i < end; i++) {
                int col = i - row * cols;
                int x = in.left + col * (cellWidth + gap);
                int w = col == cols - 1 ? width - (cols - 1) * (cellWidth + gap) : cellWidth;
                cells.get(i).setBounds(x, y, w, rowHeight);
            }
            y += rowHeight + gap;
        }
    }
}
