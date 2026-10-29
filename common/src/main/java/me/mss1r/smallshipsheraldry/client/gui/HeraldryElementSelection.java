package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** Element filters are local to a ship and sail stage, not to the table's pixel data. */
final class HeraldryElementSelection {
    private final Map<Context, Filters> contexts = new HashMap<>();
    private final BiFunction<ShipPreviewType, Integer, List<PaintableSailMesh.Element>> loadElements;
    private List<PaintableSailMesh.Element> elements = List.of();
    private Filters filters = new Filters();
    private int scroll;
    private int anchor = -1;

    HeraldryElementSelection() {
        this((type, stage) -> List.copyOf(PaintableSailMesh.elements(type, stage)));
    }

    HeraldryElementSelection(BiFunction<ShipPreviewType, Integer, List<PaintableSailMesh.Element>> loadElements) {
        this.loadElements = loadElements;
    }

    void switchContext(ShipPreviewType type, int stage) {
        filters = contexts.computeIfAbsent(new Context(type, stage), ignored -> new Filters());
        List<PaintableSailMesh.Element> sorted = new ArrayList<>(loadElements.apply(type, stage));
        sorted.sort((a, b) -> {
            int category = Integer.compare(category(a.id()), category(b.id()));
            return category != 0 ? category : a.id().compareTo(b.id());
        });
        elements = List.copyOf(sorted);
        scroll = 0;
        anchor = -1;
    }

    void clearContexts() {
        contexts.clear();
        filters = new Filters();
    }

    List<PaintableSailMesh.Element> elements() { return elements; }
    Set<String> selected() { return filters.selectedView; }
    Set<String> hidden() { return filters.hiddenView; }
    int scroll() { return scroll; }

    void scrollBy(int amount, int visibleRows) {
        scroll = Mth.clamp(scroll + amount, 0, Math.max(0, elements.size() - visibleRows));
    }

    void selectRow(int index, boolean range, boolean additive) {
        if (index < 0 || index >= elements.size()) return;
        String id = elements.get(index).id();
        if (range && anchor >= 0) {
            if (!additive) filters.selected.clear();
            for (int i = Math.min(anchor, index); i <= Math.max(anchor, index); i++) {
                filters.selected.add(elements.get(i).id());
            }
        } else if (additive) {
            if (!filters.selected.add(id)) filters.selected.remove(id);
            anchor = index;
        } else {
            filters.selected.clear();
            filters.selected.add(id);
            anchor = index;
        }
    }

    void selectHit(String id, boolean additive, boolean extend, int visibleRows) {
        if (!additive && !extend) filters.selected.clear();
        if (!filters.selected.add(id) && additive) filters.selected.remove(id);
        for (int index = 0; index < elements.size(); index++) {
            if (!elements.get(index).id().equals(id)) continue;
            if (index < scroll) scroll = index;
            else if (index >= scroll + visibleRows) scroll = Math.max(0, index - visibleRows + 1);
            anchor = index;
            break;
        }
    }

    void selectAll() {
        filters.selected.clear();
        elements.forEach(element -> filters.selected.add(element.id()));
        anchor = elements.isEmpty() ? -1 : 0;
    }

    void deselect() {
        filters.selected.clear();
        anchor = -1;
    }

    void hideSelected() { filters.hidden.addAll(filters.selected); }
    void showAll() { filters.hidden.clear(); }

    void toggleHidden(String id) {
        if (!filters.hidden.add(id)) filters.hidden.remove(id);
    }

    boolean isIsolated() {
        if (filters.selected.isEmpty()) return false;
        for (PaintableSailMesh.Element element : elements) {
            if (filters.hidden.contains(element.id()) == filters.selected.contains(element.id())) return false;
        }
        return true;
    }

    void toggleIsolate() {
        if (filters.selected.isEmpty()) return;
        if (isIsolated()) { showAll(); return; }
        filters.hidden.clear();
        elements.stream().map(PaintableSailMesh.Element::id)
                .filter(id -> !filters.selected.contains(id)).forEach(filters.hidden::add);
    }

    Component friendlyName(int index) {
        int category = category(elements.get(index).id());
        int number = 0;
        for (int i = 0; i <= index; i++) {
            if (category(elements.get(i).id()) == category) number++;
        }
        return Component.translatable(switch (category) {
            case 0 -> "smallshipsheraldry.part.sail_number";
            case 1 -> "smallshipsheraldry.part.rope_number";
            default -> "smallshipsheraldry.part.other_number";
        }, number);
    }

    private static int category(String id) {
        String name = id.toLowerCase(Locale.ROOT);
        if (name.contains("rope")) return 1;
        return name.contains("sail") || name.contains("segel") || name.contains("base") ? 0 : 2;
    }

    private record Context(ShipPreviewType ship, int stage) {
    }

    private static final class Filters {
        final Set<String> selected = new LinkedHashSet<>();
        final Set<String> hidden = new LinkedHashSet<>();
        final Set<String> selectedView = Collections.unmodifiableSet(selected);
        final Set<String> hiddenView = Collections.unmodifiableSet(hidden);
    }
}
