package io.github.direkjames.lodestock.core.market;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryMarketStorage implements MarketStorage {
    private final Map<String, ItemState> data = new HashMap<>();

    @Override public Optional<ItemState> load(String itemId) { return Optional.ofNullable(data.get(itemId)); }
    @Override public void save(String itemId, ItemState state) { data.put(itemId, state); }
    @Override public void saveAll(Map<String, ItemState> states) { data.putAll(states); }
}