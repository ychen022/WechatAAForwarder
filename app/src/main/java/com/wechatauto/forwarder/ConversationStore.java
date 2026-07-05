package com.wechatauto.forwarder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process-wide store of active conversations plus a persisted list of recent
 * messages that the UI displays.
 */
public class ConversationStore {
    private static final String FILE = "wechat_auto_store";
    private static final String KEY_RECENT = "recent_messages";
    private static final int MAX_RECENT = 60;

    private static ConversationStore instance;

    public static synchronized ConversationStore get(Context c) {
        if (instance == null) {
            instance = new ConversationStore(c.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final Map<String, Conversation> conversations = new HashMap<>();
    private int nextNotificationId = 1001;

    private ConversationStore(Context c) {
        this.appContext = c;
    }

    public synchronized Conversation getOrCreate(String key, String title, boolean group) {
        Conversation conv = conversations.get(key);
        if (conv == null) {
            conv = new Conversation(key, title, group, nextNotificationId++);
            conversations.put(key, conv);
        } else {
            conv.title = title;
            conv.group = group;
        }
        return conv;
    }

    public synchronized Conversation getByNotificationId(int id) {
        for (Conversation c : conversations.values()) {
            if (c.notificationId == id) {
                return c;
            }
        }
        return null;
    }

    // ---- Recent-message history for the UI ----

    private SharedPreferences sp() {
        return appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public synchronized void addRecent(ForwardedMessage m) {
        List<ForwardedMessage> list = getRecent();
        list.add(0, m);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        saveRecent(list);
    }

    public synchronized List<ForwardedMessage> getRecent() {
        List<ForwardedMessage> out = new ArrayList<>();
        String raw = sp().getString(KEY_RECENT, null);
        if (raw == null) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new ForwardedMessage(
                        o.optString("title"),
                        o.optString("sender"),
                        o.optString("body"),
                        o.optLong("time"),
                        o.optBoolean("group")));
            }
        } catch (JSONException ignored) {
            // corrupt data -> treat as empty
        }
        return out;
    }

    private void saveRecent(List<ForwardedMessage> list) {
        JSONArray arr = new JSONArray();
        for (ForwardedMessage m : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("title", m.conversationTitle);
                o.put("sender", m.sender);
                o.put("body", m.body);
                o.put("time", m.timestamp);
                o.put("group", m.group);
            } catch (JSONException ignored) {
                continue;
            }
            arr.put(o);
        }
        sp().edit().putString(KEY_RECENT, arr.toString()).apply();
    }

    public synchronized void clearRecent() {
        sp().edit().remove(KEY_RECENT).apply();
    }
}
