package com.example.mqtttest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.techyourchance.threadposter.UiThreadPoster;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements MqttConnectUseCase.Callback {

    MqttConnectUseCase mqttConnectUseCase;
    private int count;
    private int unthrottledCount;
    private EditText inputEditText;
    private TextView throttledCountTextView;
    private UiThreadPoster uiThreadPoster;
    private Disposable disposable;
    private ListView testList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inputEditText = findViewById(R.id.edt_input);
        throttledCountTextView = findViewById(R.id.txt_Count);
        testList = findViewById(R.id.dummy_list);
        testList.setAdapter(new ArrayAdapter<>(
                this,
                R.layout.support_simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.test_values)));
        mqttConnectUseCase = new MqttConnectUseCase(this);
        uiThreadPoster = new UiThreadPoster();
    }

    @Override
    protected void onStart() {
        mqttConnectUseCase.registerListener(this);
        mqttConnectUseCase.connectAsync();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mqttConnectUseCase.unregisterListener(this);
        super.onStop();
    }

    @Override
    public void onNewMessageReceived(MqttMessage message) {

    }

    @Override
    public void onActionSuccess(MqttConnectUseCase.Action type) {
        showToast("Mqtt action success : " + type.toString());
        switch (type) {
            case CONNECT:
                mqttConnectUseCase.subscribeAsync("android-test-topic");
                break;
            case SUBSCRIBE:
                break;
        }
    }

    @Override
    public void onActionError(MqttConnectUseCase.Action type) {
        showToast("Mqtt action success : " + type.toString());
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private Flowable<MqttMessage> MessageFlowable() {
        return Flowable.create((FlowableOnSubscribe<MqttMessage>) emitter -> {
         mqttConnectUseCase.setCallback(emitter::onNext);
        }, BackpressureStrategy.DROP);
    }

    public void onSetClick(View view) {
        String input = inputEditText.getText().toString();
        if (input.isEmpty()) {
            return;
        }
        throttledCountTextView.setText("0");
        count = 0;

        if (disposable != null) disposable.dispose();

        disposable = MessageFlowable().sample(Long.parseLong(input), TimeUnit.MILLISECONDS).
                subscribeOn(Schedulers.io()).
                observeOn(Schedulers.from(ContextCompat.getMainExecutor(this)))
                .subscribe(mqttMessage -> {
                    count++;
                    throttledCountTextView.setText(String.valueOf(count));
                });

    }


}