package com.neoranga55.rxdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.jakewharton.rx.transformer.ReplayingShare;
import com.jakewharton.rxbinding.widget.RxTextView;
import com.jakewharton.rxrelay.BehaviorRelay;
import com.jakewharton.rxrelay.PublishRelay;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeSet;
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

        RxExperiments.relaySharedWithSingleSubscribeAndUnSubscribe(mSubscriptions);

        rxSubjectPipes(mSubscriptions);

    }

    /**
     * More advanced RxJava (including Subjects and alternatives)
     */
    private void rxSubjectPipes(final CompositeSubscription subscriptions) {
        // 1- Wire the typing input from user (unlimited) into another view
        final EditText inputField = (EditText) findViewById(R.id.editText);
        final TextView resultFieldForPublishSubject = (TextView) findViewById(R.id.textView1);
        final TextView resultFieldForRxRelay = (TextView) findViewById(R.id.textView2);
        final TextView resultFieldForRxBindings = (TextView) findViewById(R.id.textView3);


        // 1.1- Using a PublishSubject
        // This is dangerous because the Rx contract has to be enforced manually:
        // - Manual thread safety
        // - Order of items emitted
        // - onComplete() and onError() events
        final PublishSubject searchSubject = PublishSubject.create();
        Subscription subs1 = searchSubject
                .compose(applyWaitAndDelayTransformation())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String res) {
                        resultFieldForPublishSubject.setText(res.toLowerCase());
                    }
                });
        subscriptions.add(subs1); // We always have to unsubscribe

        inputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Warning the CharSequence is mutable so toString creates an immutable copy
                searchSubject.onNext(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });


        // 1.2- Solve the same problem using a RxRelay library
        // This is safer because is it stateless and it can't accept terminal events complete/error
        final PublishRelay searchPublishRelay = PublishRelay.create();
        // Note: add .toSerialized() to any type of Relay object to ensure emitting(.call()) is thread safe
        Subscription subs2 = searchPublishRelay
                .compose(applyWaitAndDelayTransformation())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String res) {
                        resultFieldForRxRelay.setText(res.toUpperCase());
                    }
                });
        subscriptions.add(subs2); // We always have to unsubscribe

        inputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Warning the CharSequence is mutable so toString creates an immutable copy
                searchPublishRelay.call(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });


        // 1.3- Solve the same problem using a RxBindings library especially designed for Android UI
        Observable<CharSequence> searchRxBinding = RxTextView.textChanges(inputField);
        Subscription subs3 = searchRxBinding
                .map(CharSequence::toString) // RxBinding returns the original CharSequence
                // Warning the CharSequence is mutable so toString creates an immutable copy
                // that can be used safely and asynchronously
                .filter(charSequence -> charSequence.length() > 0)
                // Note: RxBinging emits an empty item on subscription
                // so we can overwrite with default text after filtering the empty text
                .startWith("Processed text RxBindings2")
                .compose(applyWaitAndDelayTransformation())
                .subscribe(resultFieldForRxBindings::setText);
        subscriptions.add(subs3); // We always have to unsubscribe
    }

    private <T> Observable.Transformer<T, T> applyWaitAndDelayTransformation() {
        return tObservable -> {
            return tObservable.debounce(1, TimeUnit.SECONDS) // Wait 1 second and emit the last item on that window of time
                    .observeOn(Schedulers.io())
                    // Network call should be on IO thread (fake delay() already operates in Computation thread)
                    .delay(1, TimeUnit.SECONDS) // Simulate network call
                    .observeOn(AndroidSchedulers.mainThread()); // Response needs to be on UI thread
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubscriptions.clear();
        // Note: If using .unsubscribe() the CompositeSubscription becomes useless
        // so it's better to use clear() so the CompositeSubscription is still usable
    }
}
