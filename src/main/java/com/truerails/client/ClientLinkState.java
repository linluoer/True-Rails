package com.truerails.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientLinkState {
    public record Link(int partnerId, long expireMillis) {}

    public static final Map<Integer, Link> LINKS = new ConcurrentHashMap<>();

    public static void put(int cartA, int cartB, long expireMillis) {
        LINKS.put(cartA, new Link(cartB, expireMillis));
    }

    public static void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Link>> it = LINKS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expireMillis() < now) it.remove();
        }
    }

    public static Map<Integer, List<Integer>> adjacency() {
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (Map.Entry<Integer, Link> e : LINKS.entrySet()) {
            adj.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue().partnerId());
            adj.computeIfAbsent(e.getValue().partnerId(), k -> new ArrayList<>()).add(e.getKey());
        }
        return adj;
    }

    private ClientLinkState() {}
}
