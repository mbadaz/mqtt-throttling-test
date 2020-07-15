package com.example.mqtttest;

import android.content.Context;
import android.util.Log;

import com.techyourchance.threadposter.BackgroundThreadPoster;
import com.techyourchance.threadposter.UiThreadPoster;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.core.FlowableSubscriber;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MqttConnectUseCase extends BaseBusyObservable<MqttConnectUseCase.Callback>
        implements IMqttActionListener, MqttCallbackExtended {

    public interface Callback {
        void onNewMessageReceived(MqttMessage message);

        void onActionSuccess(Action type);

        void onActionError(Action type);
    }

    public interface NewMessageCallback{
        void onNewMessage(MqttMessage mqttMessage);
    }

    private NewMessageCallback callback;

    private static final String TAG = MqttConnectUseCase.class.getSimpleName();

    public enum Action {
        CONNECT, SUBSCRIBE, PUBLISH
    }

    private static FlowableEmitter<MqttMessage> flowableEmitter;
    private final MqttAsyncClient mqttAndroidClient;
    private final BackgroundThreadPoster backgroundThreadPoster = new BackgroundThreadPoster();
    private final UiThreadPoster uiThreadPoster = new UiThreadPoster();
    private static int count = 0;


    public MqttConnectUseCase(Context context) {

        this.mqttAndroidClient = initialize();
        if (mqttAndroidClient != null) {
            mqttAndroidClient.setCallback(this);
        }
    }

    private MqttAsyncClient initialize() {
        try {
            return new MqttAsyncClient("tcp://onlinetaxi.co.za",
                    MqttClient.generateClientId(), new MemoryPersistence());
        } catch (MqttException e) {
            Log.e("MQTT error","error", e);
        }
        return null;
    }

    public void setCallback(NewMessageCallback callback) {
        this.callback = callback;
    }

    public void connectAsync() {
        if (mqttAndroidClient.isConnected()) {
            for (Callback callback : getListeners()) {
                callback.onActionSuccess(Action.CONNECT);
            }
            return;
        }

        if (!isBusy()) {
            assertNotBusyAndBecomeBusy();
            connect();
        }
    }

    private void connect() {
        backgroundThreadPoster.post(() -> {
            try {
                MqttConnectOptions connectOptions = new MqttConnectOptions();
                connectOptions.setCleanSession(false);
                connectOptions.setAutomaticReconnect(true);
                connectOptions.setMaxInflight(50);
                IMqttToken token = mqttAndroidClient.connect(connectOptions);
                token.setUserContext(Action.CONNECT);
                token.setActionCallback(this);
            } catch (MqttException e) {
                uiThreadPoster.post(() -> {
                    for (Callback callback : getListeners()) {
                        callback.onActionError(Action.CONNECT);
                    }
                });
                e.printStackTrace();
            }
        });

    }

    public void subscribeAsync(String topic) {
        backgroundThreadPoster.post(() -> subscribe(topic));
    }

    private void subscribe(String topic) {
        try {
            IMqttToken iMqttToken = mqttAndroidClient.subscribe(topic, 0);
            iMqttToken.setUserContext(Action.SUBSCRIBE);
            iMqttToken.setActionCallback(this);
        } catch (MqttException e) {
            e.printStackTrace();
            uiThreadPoster.post(() -> {
                for (Callback callback : getListeners()) {
                    callback.onActionError(Action.SUBSCRIBE);
                }
            });
        }
    }

    public void publish(String topic, String message) {
        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setQos(1);
        try {
            mqttAndroidClient.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
            notifyFailed(Action.PUBLISH);
        }
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        Action action = (Action) asyncActionToken.getUserContext();
        uiThreadPoster.post(() -> {
            for (Callback callback : getListeners()) {
                callback.onActionSuccess(action);
            }
        });
        becomeNotBusy();
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Action action = (Action) asyncActionToken.getUserContext();
        uiThreadPoster.post(() -> {
            for (Callback callback : getListeners()) {
                callback.onActionError(action);
            }
        });
        Log.d(TAG, "Action failed: " + action.toString());
        becomeNotBusy();
    }

    @Override
    public void connectionLost(Throwable cause) {
        // TODO Implement what happens
        Log.e(TAG, "Mqtt connection lost", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
//        uiThreadPoster.post(() -> {
//            for (Callback callback : getListeners()) {
//                callback.onNewMessageReceived(message);
//            }
//        });
        callback.onNewMessage(message);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
        disconnectedBufferOptions.setBufferEnabled(true);
        disconnectedBufferOptions.setBufferSize(100);
        disconnectedBufferOptions.setPersistBuffer(false);
        disconnectedBufferOptions.setDeleteOldestMessages(false);
        mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
        notifySuccess(Action.CONNECT);

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        try {
            if (token.getMessage() == null) {
                Log.d(TAG, "Publish success");
                notifySuccess(Action.PUBLISH);
            }
        } catch (MqttException e) {
            notifyFailed(Action.PUBLISH);
        }
    }

    private void notifySuccess(Action action) {
        uiThreadPoster.post(() -> {
            for (Callback callback : getListeners()) {
                callback.onActionSuccess(action);
            }
        });
        becomeNotBusy();
    }

    private void notifyFailed(Action action) {
        uiThreadPoster.post(() -> {
            for (Callback callback : getListeners()) {
                callback.onActionError(action);
            }
        });
        becomeNotBusy();
    }


}
