package com.neoranga55.rxdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

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

        // FlatMap transforms one type of stream
        // (not just the observable objects but the actual stream) into another
        query("Hello, world!")
                .flatMap(Observable::from)
                .subscribe(System.out::println);

        // Advanced FlatMap versus Map
        query("Hello, world!")
                .flatMap(Observable::from)
                // Simply transform each String item into an Observable that emits a new String
                // String1 -> Observable(String2)
                .map(this::getTitle)
                .subscribe(System.out::println);
        // Prints each Observable object reference once

        query("Hello, world!")
                .flatMap(Observable::from)
                // Simply transform each String item into an Observable that emits a new String
                // String1 -> Observable(String2)
                .flatMap(this::getTitle)
                .subscribe(System.out::println);
        // Prints each transformed String once

        query("Hello, world!")
                .flatMap(Observable::from)
                // Transform each String item into an Observable which emits two new Strings
                // String1 -> Observable(String2, String3) -> String2, String3
                .flatMap(this::getTitleStream)
                .filter(title -> title != null)
                .take(11)
                .doOnNext(this::storeItem)
                .subscribe(System.out::println);
        // Prints two transformed Strings and skips null Strings
    }

    private Observable<List<String>> query(String query) {
        final ArrayList<String> urls = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            urls.add("Query: " + query + " " + i);
        }
        return Observable.just(urls);
    }

    // This method is compatible with map() and flatMap() because
    // it returns an Observable (for flatMap) but it emits only one String (for map)
    private Observable<String> getTitle(String URL) {
        return Observable.just("http://" + URL.replaceAll(" ",""));
    }

    // This method is only compatible with flatMap() because
    // the returned Observable emits more than one element
    // Each Observable emits the received element twice (with extra text)
    private Observable<String> getTitleStream(String URL) {
        return Observable.create(
                new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> sub) {
                        if (!sub.isUnsubscribed()) {
                            sub.onNext("http://" + URL.replaceAll(" ",""));
                            sub.onNext("http://" + URL.replaceAll(" ","") + "-Bis");
                            if (URL.contains("4")) {
                                sub.onNext(null); // Simulate null item returned on a single query
                            }
                            sub.onCompleted();
                        }
                    }
                }
        );
    }

    private void storeItem(String s) {
        System.out.println("I'm storing item: " + s);
    }
}
