package com.wechatauto.forwarder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvEmpty;
    private SwitchCompat swForwarding;
    private SwitchCompat swGroupSplit;
    private RecyclerView rvMessages;
    private RecentMessagesAdapter adapter;

    private final ActivityResultLauncher<String> postNotifLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> updateStatus());

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatus();
            refreshList();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvEmpty = findViewById(R.id.tvEmpty);
        swForwarding = findViewById(R.id.swForwarding);
        swGroupSplit = findViewById(R.id.swGroupSplit);
        rvMessages = findViewById(R.id.rvMessages);

        adapter = new RecentMessagesAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        Button btnAccess = findViewById(R.id.btnNotificationAccess);
        btnAccess.setOnClickListener(v -> openNotificationAccessSettings());

        Button btnTest = findViewById(R.id.btnTest);
        btnTest.setOnClickListener(v -> sendTestMessage());

        Button btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(v -> {
            ConversationStore.get(this).clearRecent();
            refreshList();
        });

        swForwarding.setChecked(Prefs.isForwardingEnabled(this));
        swForwarding.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setForwardingEnabled(this, checked);
            updateStatus();
        });

        swGroupSplit.setChecked(Prefs.isGroupSplitEnabled(this));
        swGroupSplit.setOnCheckedChangeListener((b, checked) ->
                Prefs.setGroupSplitEnabled(this, checked));

        CheckBox cbWeChat = findViewById(R.id.cbWeChat);
        cbWeChat.setChecked(Prefs.isAppEnabled(this, SupportedApp.WECHAT));
        cbWeChat.setOnCheckedChangeListener((b, checked) ->
                Prefs.setAppEnabled(this, SupportedApp.WECHAT, checked));

        CheckBox cbTeams = findViewById(R.id.cbTeams);
        cbTeams.setChecked(Prefs.isAppEnabled(this, SupportedApp.TEAMS));
        cbTeams.setOnCheckedChangeListener((b, checked) ->
                Prefs.setAppEnabled(this, SupportedApp.TEAMS, checked));

        maybeRequestPostNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MessageNotificationListenerService.ACTION_STATE_CHANGED);
        filter.addAction(MessageNotificationListenerService.ACTION_MESSAGE_FORWARDED);
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter);
        updateStatus();
        refreshList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver);
    }

    private void openNotificationAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void maybeRequestPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasPostNotificationsPermission()) {
            postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void sendTestMessage() {
        ConversationStore store = ConversationStore.get(this);
        long now = System.currentTimeMillis();
        String sender = getString(R.string.test_sender);
        String body = getString(R.string.test_body);

        Conversation conv = store.getOrCreate("__test__:" + sender, sender, false);
        conv.appLabel = SupportedApp.WECHAT.label;
        conv.lastBody = body;
        conv.lastPostTime = now;
        conv.addMessage(new Conversation.Msg(sender, body, now, false));

        new CarNotificationForwarder(this).post(conv);
        store.addRecent(new ForwardedMessage(
                SupportedApp.WECHAT.label, sender, sender, body, now, false));
        refreshList();

        int msg = hasPostNotificationsPermission()
                ? R.string.status_post_granted : R.string.status_post_denied;
        Toast.makeText(this,
                hasPostNotificationsPermission() ? "已发送测试消息" : getString(msg),
                Toast.LENGTH_SHORT).show();
    }

    private void refreshList() {
        List<ForwardedMessage> recent = ConversationStore.get(this).getRecent();
        adapter.setItems(recent);
        tvEmpty.setVisibility(recent.isEmpty() ? TextView.VISIBLE : TextView.GONE);
    }

    private void updateStatus() {
        boolean access = isNotificationAccessGranted();
        boolean listener = MessageNotificationListenerService.isConnected();
        boolean forwarding = Prefs.isForwardingEnabled(this);
        boolean post = hasPostNotificationsPermission();

        StringBuilder sb = new StringBuilder();
        sb.append(mark(access)).append(' ')
                .append(getString(access ? R.string.status_access_granted
                        : R.string.status_access_denied)).append('\n');
        sb.append(mark(listener)).append(' ')
                .append(getString(listener ? R.string.status_listener_connected
                        : R.string.status_listener_disconnected)).append('\n');
        sb.append(mark(post)).append(' ')
                .append(getString(post ? R.string.status_post_granted
                        : R.string.status_post_denied)).append('\n');
        sb.append(mark(forwarding)).append(' ')
                .append(getString(forwarding ? R.string.status_forwarding_on
                        : R.string.status_forwarding_off));

        tvStatus.setText(sb.toString());
    }

    private String mark(boolean ok) {
        return ok ? "\u2705" : "\u274C";
    }

    private boolean isNotificationAccessGranted() {
        return NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(getPackageName());
    }

    private boolean hasPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
