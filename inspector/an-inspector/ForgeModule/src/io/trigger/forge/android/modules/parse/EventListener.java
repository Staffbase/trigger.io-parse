package io.trigger.forge.android.modules.parse;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParsePush;
import com.parse.SaveCallback;
import com.parse.VisibilityManager;

import io.trigger.forge.android.core.ForgeApp;
import io.trigger.forge.android.core.ForgeEventListener;
import io.trigger.forge.android.core.ForgeLog;

public class EventListener extends ForgeEventListener {
    @Override
    public void onApplicationCreate() {
        try {
            ForgeLog.d("com.parse.push onApplicationCreate");

            // build configurations (fail fast if something is wrong)
            final JsonObject config = ForgeApp.configForPlugin(Constant.MODULE_NAME);
            final Context appContext = ForgeApp.getApp();
            final FirebaseOptions firebaseOptions = createFirebaseOptions(config);
            final Parse.Configuration parseConfig = createParseConfig(config, appContext);

            // init Firebase and wait until deviceToken is available
            FirebaseApp firebaseApp = FirebaseApp.initializeApp(ForgeApp.getApp(), firebaseOptions);
            Task<InstanceIdResult> instanceIdTask = FirebaseInstanceId.getInstance().getInstanceId();
            instanceIdTask.addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                @Override
                public void onComplete(@NonNull Task<InstanceIdResult> task) {
                    // the deviceToken is available -> init parse
                    ForgeLog.d("com.parse.push obtained deviceToken: " + task.getResult().getToken());
                    initParse(parseConfig);
                }
            });

        } catch (Exception ex) {
            // In case of unexpected exception, we want to log it and pass it up
            ForgeLog.e("com.parse.push onApplicationCreate failed: " + ex);
            throw ex;
        }
    }

    private FirebaseOptions createFirebaseOptions(final JsonObject config) {
        final JsonObject androidConfig = config.get("android").getAsJsonObject();
        final JsonObject firebaseConfig = androidConfig.has("firebase") ? androidConfig.get("firebase").getAsJsonObject() : new JsonObject();

        if (!firebaseConfig.has("apiKey")) {
            throw new IllegalStateException("'android.firebase.apiKey' is missing");
        }
        if (!firebaseConfig.has("applicationId")) {
            throw new IllegalStateException("'android.firebase.applicationId' is missing");
        }

        final String firebaseApiKey = firebaseConfig.get("apiKey").getAsString();
        final String firebaseAppId = firebaseConfig.get("applicationId").getAsString();

        ForgeLog.d("com.parse.push Firebase config set as:" + firebaseConfig.toString());
        return new FirebaseOptions.Builder()
                .setApplicationId(firebaseAppId)
                .setApiKey(firebaseApiKey)
                .build();
    }

    private Parse.Configuration createParseConfig(final JsonObject config, final Context appContext) {
        if (!config.has("server")) {
            throw new IllegalStateException("'server' is missing");
        }
        String server = config.get("server").getAsString();

        final String applicationId = config.has("applicationId")
                ? config.get("applicationId").getAsString()
                : "";

        final String clientKey = config.has("clientKey")
                ? ForgeApp.configForPlugin(Constant.MODULE_NAME).get("clientKey").getAsString()
                : null;

        ForgeLog.d("com.parse.push building parse configuration for " + server);
        return new Parse.Configuration.Builder(appContext)
                .server(server)
                .applicationId(applicationId)
                .clientKey(clientKey)
                .build();
    }

    private void initFirebase(final JsonObject config) {
    }

    private void initParse(Parse.Configuration parseConfig) {
        ForgeLog.d("com.parse.push initializing with parse");
        Parse.initialize(parseConfig);

        ParseInstallation.getCurrentInstallation().saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (e != null) {
                    ForgeLog.e("com.parse.push failed to initialize: " + e.getLocalizedMessage());
                    e.printStackTrace();
                    return;
                }

                Object deviceToken = ParseInstallation.getCurrentInstallation().getDeviceToken();
                if (deviceToken == null) {
                    deviceToken = ParseInstallation.getCurrentInstallation().getDeviceToken();
                    ForgeLog.e("com.parse.push deviceToken is null :-( " + deviceToken);
                    return;
                }

                ForgeLog.i("com.parse.push initialized successfully, deviceToken: " + deviceToken);

                ParsePush.subscribeInBackground("", new SaveCallback() {
                    @Override
                    public void done(com.parse.ParseException e) {
                        if (e != null) {
                            ForgeLog.e("com.parse.push failed to subscribe for push: " + e.getLocalizedMessage());
                            e.printStackTrace();
                            return;
                        }
                        ForgeLog.i("com.parse.push successfully subscribed to the broadcast channel.");
                    }
                });
            }
        });

        ForgeLog.i("com.parse.push Initializing Parse and subscribing to default channel ...");
    }


    @Override
    public void onNewIntent(Intent intent) {
        if (intent.getExtras() != null && intent.getExtras().get("com.parse.Data") != null) {
            ForgeApp.event("event.messagePushed", new JsonParser().parse((String) intent.getExtras().get("com.parse.Data")));
        }
    }

    @Override
    public void onStart() {
        ForgeLog.d("com.parse.push onStart");
        VisibilityManager.resumed();
    }

    @Override
    public void onStop() {
        ForgeLog.d("com.parse.push onStop");
        VisibilityManager.paused();
    }
}
