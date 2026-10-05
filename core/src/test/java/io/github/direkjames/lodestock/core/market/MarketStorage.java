package io.github.direkjames.lodestock.core.market;

import java.util.Map;
import java.util.Optional;

/** Where live prices and stock are kept. YAML now, SQL in Phase 2. */
public interface MarketStorage {
    Optional<ItemState> load(String itemId);
    void save(String itemId, ItemState state);
    void saveAll(Map<String, ItemState> states);
    default void close() {}
}