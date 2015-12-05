package com.neoranga55.rxdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Basic Rx 'Hello world'
        Observable<String> myObservable = Observable.create(
                new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> sub) {
                        sub.onNext("Hello, world!");
                        sub.onCompleted();
                    }
                }
        );
        Subscriber<String> mySubscriber = new Subscriber<String>() {
            @Override
            public void onNext(String s) { System.out.println(s); }

            @Override
            public void onCompleted() { }

            @Override
            public void onError(Throwable e) { }
        };
        myObservable.subscribe(mySubscriber);

        // Shorter observable and subscriber
        Observable.just("Short hello, world!").subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                System.out.println(s);
            }
        });

        // Shorten subscriber with lambdas with retrolambda library and basic map transformation
        Observable.just("Short hello, world!").map( s -> s + " By Sebas" ).subscribe( s -> System.out.println(s));

        // Map transformations of multiple types
        Observable.just("Hello, world!")
                .map(s -> s + " By Sebas")
                .map(String::hashCode)
                .map(i -> Integer.toString(i))
                .subscribe(System.out::println);
    }
}
