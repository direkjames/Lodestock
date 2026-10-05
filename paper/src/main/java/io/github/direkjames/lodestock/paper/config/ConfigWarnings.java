package io.github.direkjames.lodestock.paper.config;

import java.util.List;
import java.util.logging.Logger;

public final class ConfigWarnings {
    private final Logger log;
    private final List<String> list;

    public ConfigWarnings(Logger log, List<String> list) {
        this.log = log;
        this.list = list;
    }

    public void add(String message) {
        log.warning(message);
        list.add(message);
    }
}