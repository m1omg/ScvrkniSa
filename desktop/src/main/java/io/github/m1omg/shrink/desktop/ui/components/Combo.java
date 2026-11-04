package io.github.m1omg.shrink.desktop.ui.components;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A combo box that shows each item through a label function (items can be any objects). */
public final class Combo<T> extends JComboBox<T> {
    private static final long serialVersionUID = 1L;

    private transient Function<T, String> label;

    public Combo(List<T> items, Function<T, String> label) {
        this.label = label;
        for (T item : items) {
            addItem(item);
        }
        setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? Combo.this.label.apply(null) : Combo.this.label.apply((T) value));
                return this;
            }
        });
    }

    @SuppressWarnings("unchecked")
    public T selected() {
        return (T) getSelectedItem();
    }

    /** Selects the item equal to {@code value} (or the first one). */
    public void select(T value) {
        for (int i = 0; i < getItemCount(); i++) {
            if (Objects.equals(getItemAt(i), value)) {
                setSelectedIndex(i);
                return;
            }
        }
        if (getItemCount() > 0) {
            setSelectedIndex(0);
        }
    }

    public void setItems(List<T> items) {
        T current = selected();
        removeAllItems();
        for (T item : items) {
            addItem(item);
        }
        select(current);
    }
}
