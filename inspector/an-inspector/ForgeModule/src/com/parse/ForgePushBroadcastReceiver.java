package com.parse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import io.trigger.forge.android.core.ForgeActivity;
import io.trigger.forge.android.core.ForgeApp;
import io.trigger.forge.android.core.ForgeLog;
import io.trigger.forge.android.modules.parse.Constant;

public class ForgePushBroadcastReceiver extends ParsePushBroadcastReceiver {
    private static final String UPDATE_NOTIFICATIONS_FEATURE = "updateNotifications";
    private static final String SHOW_NOTIFICATIONS_WHILE_VISIBLE = "showNotificationsWhileVisible";

    private static final String notificationChannelId = "default";
    private static final String notificationChannelDescription = "Default";

    static ArrayList<HashMap<String, String>> history = new ArrayList<>();

    private boolean isUpdateNotificationsFeature() {
        JsonObject config = ForgeApp.configForModule(Constant.MODULE_NAME);

        return config.has("android") &&
               config.getAsJsonObject("android").has(UPDATE_NOTIFICATIONS_FEATURE) &&
               config.getAsJsonObject("android").get(UPDATE_NOTIFICATIONS_FEATURE).getAsBoolean();
    }

    private boolean showNotificationsWhileVisible() {
        JsonObject config = ForgeApp.configForModule(Constant.MODULE_NAME);

        return config.has("android") &&
                config.getAsJsonObject("android").has(SHOW_NOTIFICATIONS_WHILE_VISIBLE) &&
                config.getAsJsonObject("android").get(SHOW_NOTIFICATIONS_WHILE_VISIBLE).getAsBoolean();
    }

    private NotificationCompat.Builder setBackgroundColor(NotificationCompat.Builder notification) {
        JsonObject config = ForgeApp.configForModule(Constant.MODULE_NAME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            config.has("android") &&
            config.getAsJsonObject("android").has("background-color")) {
            try {
                notification.setColor(Color.parseColor(config.getAsJsonObject("android").get("background-color").getAsString()));
            } catch (IllegalArgumentException e) {
                ForgeLog.e("Invalid color string for parse.android.background-color: " + e.getMessage());
            }
        }
        return notification;
    }

    @Override
    public void onPushOpen(Context context, Intent intent) {
        ParseAnalytics.trackAppOpenedInBackground(intent);

        // can't we just call super?
        Intent activity = new Intent(context, ForgeActivity.class);
        activity.putExtras(intent.getExtras());
        activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activity);

        if (isUpdateNotificationsFeature()) {
            history.clear();
        }
    }

    @Override
    protected void onPushDismiss(Context context, Intent intent) {
        super.onPushDismiss(context, intent);

        if (isUpdateNotificationsFeature()) {
            history.clear();
        }
    }

    @Override
    protected NotificationCompat.Builder getNotification(Context context, Intent intent) {
        if (VisibilityManager.isVisible() && !showNotificationsWhileVisible()) {
            return null;
        }

        if (isUpdateNotificationsFeature()) {
            buildAndShowUpdatableNotification(context, intent);
            return null;
        }

        return setBackgroundColor(super.getNotification(context, intent));
    }

    protected void buildAndShowUpdatableNotification(Context context, Intent intent) {
        JSONObject pushData = this.getPushData(intent);
        if (pushData != null && (pushData.has("alert") || pushData.has("title"))) {
            HashMap<String, String> message = new HashMap<>();
            message.put("alert", pushData.optString("alert", "Notification received."));
            message.put("title", pushData.optString("title", ManifestInfo.getDisplayName(context)));
            history.add(message);

            Bundle extras = intent.getExtras();
            Random random = new Random();
            int contentIntentRequestCode = random.nextInt();
            int deleteIntentRequestCode = random.nextInt();
            String packageName = context.getPackageName();
            Intent contentIntent = new Intent("com.parse.push.intent.OPEN");
            contentIntent.putExtras(extras);
            contentIntent.setPackage(packageName);
            Intent deleteIntent = new Intent("com.parse.push.intent.DELETE");
            deleteIntent.putExtras(extras);
            deleteIntent.setPackage(packageName);
            PendingIntent pContentIntent = PendingIntent.getBroadcast(context, contentIntentRequestCode, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent pDeleteIntent = PendingIntent.getBroadcast(context, deleteIntentRequestCode, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, notificationChannelId);

            builder.setContentTitle(message.get("title"))
                   .setContentText(message.get("alert"))
                   .setSmallIcon(this.getSmallIconId(context, intent))
                   .setLargeIcon(this.getLargeIcon(context, intent))
                   .setContentIntent(pContentIntent)
                   .setDeleteIntent(pDeleteIntent)
                   .setAutoCancel(true)
                   .setDefaults(-1);

            if (history.size() > 1) {
                NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                inboxStyle.setSummaryText(history.size() + " messages received");
                builder.setContentText(history.size() + " messages received");

                // Add each form submission to the inbox list
                for (int i = 0; i < history.size(); i++) {
                    inboxStyle.addLine(history.get(i).get("alert"));
                }
                builder.setStyle(inboxStyle);
            } else {
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(message.get("alert")));
            }

            // create notification manager
            NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(notificationChannelId,
                        notificationChannelDescription,
                        NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }

            // send notification
            Notification notification = setBackgroundColor(builder).build();
            try {
                notificationManager.notify(1, notification);
            } catch (SecurityException var6) {
                notification.defaults = 5;
                notificationManager.notify(1, notification);
            }
        }
    }

    @Override
    protected JSONObject getPushData(Intent intent) {
        try {
            return new JSONObject(intent.getStringExtra("com.parse.Data"));
        } catch (JSONException e) {
            ForgeLog.e("com.parse.ParsePushReceiver: Unexpected JSONException when receiving push data: " + e.getLocalizedMessage());
            return null;
        }
    }
}
