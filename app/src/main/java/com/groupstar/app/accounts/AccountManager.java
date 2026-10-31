package com.groupstar.app.accounts;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * يدير قائمة التوكنات — حفظ، تبديل، حذف — بدون تسجيل خروج
 */
public class AccountManager {

    private static final String PREFS = "gs_accounts";
    private static final String KEY_ACCOUNTS = "accounts_list";
    private static final String KEY_ACTIVE = "active_index";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private List<Account> accounts;
    private int activeIndex;

    public AccountManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    // ── تحميل من الذاكرة ────────────────────────────────────────
    private void load() {
        String json = prefs.getString(KEY_ACCOUNTS, null);
        activeIndex = prefs.getInt(KEY_ACTIVE, 0);
        if (json == null) {
            accounts = new ArrayList<>();
        } else {
            Type t = new TypeToken<List<Account>>(){}.getType();
            accounts = gson.fromJson(json, t);
            if (accounts == null) accounts = new ArrayList<>();
        }
    }

    // ── حفظ ─────────────────────────────────────────────────────
    private void save() {
        prefs.edit()
            .putString(KEY_ACCOUNTS, gson.toJson(accounts))
            .putInt(KEY_ACTIVE, activeIndex)
            .apply();
    }

    // ── إضافة توكن ──────────────────────────────────────────────
    public int addAccount(String token, String nickname) {
        // تحقق من التكرار
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).token.equals(token)) return i;
        }
        accounts.add(new Account(token, nickname.isEmpty() ? "Account " + (accounts.size() + 1) : nickname));
        save();
        return accounts.size() - 1;
    }

    // ── تبديل الحساب النشط ──────────────────────────────────────
    public Account switchTo(int index) {
        if (index < 0 || index >= accounts.size()) return null;
        activeIndex = index;
        save();
        return accounts.get(activeIndex);
    }

    // ── حذف ─────────────────────────────────────────────────────
    public void remove(int index) {
        if (index < 0 || index >= accounts.size()) return;
        accounts.remove(index);
        if (activeIndex >= accounts.size()) activeIndex = accounts.size() - 1;
        save();
    }

    // ── Getters ──────────────────────────────────────────────────
    public List<Account> getAll() { return accounts; }
    public Account getActive() {
        if (accounts.isEmpty()) return null;
        if (activeIndex >= accounts.size()) activeIndex = 0;
        return accounts.get(activeIndex);
    }
    public int getActiveIndex() { return activeIndex; }
    public boolean isEmpty() { return accounts.isEmpty(); }

    // ── تحديث nickname ───────────────────────────────────────────
    public void updateNickname(int index, String nickname) {
        if (index < 0 || index >= accounts.size()) return;
        accounts.get(index).nickname = nickname;
        save();
    }

    // ═══════════════════════════════════════════════════════════
    public static class Account {
        public String token;
        public String nickname;
        public long addedAt;

        public Account(String token, String nickname) {
            this.token = token;
            this.nickname = nickname;
            this.addedAt = System.currentTimeMillis();
        }
    }
}
