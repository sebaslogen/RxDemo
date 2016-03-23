package com.neoranga55.rxdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;

@SuppressWarnings("SameReturnValue")
public class MainActivity extends AppCompatActivity {

    /**
     * Store all subscription to un-subscribe from all existing subscriptions
     * when the Activity is destroyed and avoid memory leaks
     */
    private CompositeSubscription mSubscriptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSubscriptions = new CompositeSubscription();

        // Run all Rx demos in separate thread and handle only the returned value
        // (no errors processed but fromCallable automatically handles exception throwing)
        final Subscription subs1 = Observable.fromCallable(RxExperiments::runRxDemos)
                .subscribeOn(Schedulers.newThread()) // Everything above this runs on a new thread
                .observeOn(AndroidSchedulers.mainThread()) // Everything below runs on main thread
                .subscribe(System.out::println);
        mSubscriptions.add(subs1);

        mSubscriptions.add(RxExperiments.deferDemo());

        mSubscriptions.add(RxExperiments.deferExceptionDemo());

        mSubscriptions.add(RxExperiments.deferExceptionDemoWithErrorHandling());

        // More advanced RxJava (including Subjects)
        final EditText inputField = (EditText) findViewById(R.id.editText);
        final TextView resultField = (TextView) findViewById(R.id.textView);
        final PublishSubject searchSubject = PublishSubject.create();
        searchSubject
                .debounce(1, TimeUnit.SECONDS) // Wait 1 second and emit the last item on that window of time
                .observeOn(Schedulers.io()) // Network call should be on another thread, not on UI thread
                .map(s -> {
                    try { // Simulate network call
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return s;
                })
                .observeOn(AndroidSchedulers.mainThread()) // Response needs to be on UI thread
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String res) {
                        resultField.setText(res);
                    }
                });

        inputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchSubject.onNext(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubscriptions.clear();
        // Note: If using .unsubscribe() the CompositeSubscription becomes useless
        // so it's better to use clear() so the CompositeSubscription is still usable
    }
}
