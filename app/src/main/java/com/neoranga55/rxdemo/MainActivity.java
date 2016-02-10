package com.neoranga55.rxdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.exceptions.Exceptions;
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
        final Subscription subs1 = Observable.fromCallable(this::runRxDemos)
                .subscribeOn(Schedulers.newThread()) // Everything above this runs on a new thread
                .observeOn(AndroidSchedulers.mainThread()) // Everything below runs on main thread
                .subscribe(System.out::println);
        mSubscriptions.add(subs1);

        // Defer execution of a method and handle possible errors
        final Subscription subs2 = Observable.defer(() -> {
                    try {
                        return Observable.just(deferDemo());
                    } catch (Exception e) { // No error is actually forwarded here
                        return Observable.error(e);
                    }
                })
                .map(input -> {
                    try {
                        return input;
                    } catch (Throwable t) { // How to handle/propagate exceptions in map methods
                        throw Exceptions.propagate(t);
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(System.out::println);
        mSubscriptions.add(subs2);

        // Defer execution of a method and forward errors to subscriber
        final Subscription subs3 = Observable.defer(() -> Observable.just(deferExceptionDemo()))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        System.out::println,
                        throwable -> {
                            System.out.println("Exception error correctly processed");
                        });
        mSubscriptions.add(subs3);

        // Defer execution of a method and handle error inside Observable
        final Subscription subs4 = Observable.defer(() -> Observable.just(deferExceptionDemo()))
                .onErrorReturn( // The events above in the stream will still stop emitting items because they saw the onError event
                        error -> "Exception processed by Observable and this value is returned instead")
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(System.out::println);
        mSubscriptions.add(subs4);

        // More advanced RxJava (including Subjects)
        final EditText inputField = (EditText) findViewById(R.id.editText);
        final TextView resultField = (TextView) findViewById(R.id.textView);
        final PublishSubject searchSubject = PublishSubject.create();
        searchSubject
                .debounce(1000, TimeUnit.MILLISECONDS)
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

    private String deferExceptionDemo() {
        throw new ArrayIndexOutOfBoundsException();
    }

    private String deferDemo() {
        return "deferDemo completed successfully";
    }

    private String runRxDemos() {
        // Basic Rx 'Hello world'
        Observable<String> myObservable = Observable.create(
                new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> sub) {
                        if (!sub.isUnsubscribed()) { // Fix to behave like Observable.just()
                            sub.onNext("Hello, world!");
                            sub.onCompleted();
                        }
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
        Subscription subscription = myObservable.subscribe(mySubscriber);
        subscription.unsubscribe(); // This stops subscription if Observable was still emitting items

        // Shorter observable and subscriber
        Observable.just("Short hello, world!").subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                System.out.println(s);
            }
        });

        // Shorten subscriber with lambdas with retrolambda library and basic map transformation
        Observable.just("Short hello, world!").map( s -> s + " By Sebas" ).subscribe( s -> System.out.println(s));

        // Concatenate two observables (both of them will be emitted in sequential order
        // This is equivalent to Observable.concat(observable_1, observable_2)
        Observable.just("First sequential hello, world!").concatWith(  Observable.just("Concatenated hello, world!") ).subscribe( s -> System.out.println(s));

        // Map transformations of multiple types
        Observable.just("Hello, world!")
                .map(s -> s + " By Sebas")
                .map(String::hashCode)
                .map(i -> Integer.toString(i))
                .subscribe(System.out::println);

        // FlatMap transforms each item received (of input type A)
        // into another stream of objects (of output type B)
        // and then flattens (mixes) all new streams into a single stream (of output type B)
        // (there is a new stream/observable produced for each input item received in flatMap)
        //    Warning:    flatMap doesn't respect order (it uses merging),
        // so if flatMap calls an asynchronous method that returns async results
        // the resulting sequence might not respect the sequence of items emitted from
        // original observable before flatMap. To enforce sequence use concatMap instead of flatMap
        query("Hello, world! flatMap")
                .flatMap(Observable::from)
                .subscribe(System.out::println);

        // ConcatMap
        // This will produce the same output as above but ensure sequential order is respected
        query("Hello, world! concatMap")
                .concatMap(Observable::from)
                .subscribe(System.out::println);

        // Basic difference between map and flatMap
        Observable.just(1,2,3)
                .map(i -> "Num:" + Integer.toString(i))
                .subscribe(System.out::println);
        Observable.just(1,2,3,4,5)
                .flatMap(this::numToString)
                .subscribe(System.out::println);

        // Advanced FlatMap versus Map
        query("Hello, world! flatMap VS map")
                .flatMap(Observable::from)
                // Simply transform each String item into an Observable that emits a new String
                // String1 -> Observable(String2)
                .map(this::getTitle)
                .subscribe(System.out::println);
        // Prints each Observable object reference once

        query("Hello, world! flatMap + flatMap")
                .flatMap(Observable::from)
                // Simply transform each String item into an Observable that emits a new String
                // String1 -> Observable(String2)
                .flatMap(this::getTitle)
                .subscribe(System.out::println);
        // Prints each transformed String once

        query("Hello, world! flatMap + flatMap + filter + take + store")
                .flatMap(Observable::from)
                // Transform each String item into an Observable which emits two new Strings
                // String1 -> Observable(String2, String3) -> String2, String3
                .flatMap(this::getTitleStream)
                .filter(title -> title != null)
                .take(11)
                .doOnNext(this::storeItem)
                .subscribe(System.out::println);
        // Prints two transformed Strings and skips null Strings

        rxCache();

        return "runRxDemos completed successfully";
    }

    /**
     * Cache values and emit always the same from the cache,
     * non cached instances keep receiving new items
     */
    private void rxCache() {
        // Create a new observable that emits one integer on each subscribe.
        // The number indicates how many times the subscribe() has been called.
        Observable<Integer> observable = Observable.create(new Observable.OnSubscribe<Integer>() {
            private int counter = 0;

            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                subscriber.onNext(counter++); // Give the next counter value synchronously.
                // Normally it would be nice to complete the observable, but for
                // illustrative purposes we'll leave the subscriptions as non-terminating.
                // observer.onCompleted();
            }
        });

        // Subscriptions to the original observable increment the value
        observable.subscribe(integer -> { // Increased value
            System.out.println("Emitted counter in non-cached Observer 1: " + integer);
        });
        // Create a cached observable that saves all values it receives from
        // the original source and gives the forward to all of its subscribers.
        Observable<Integer> cachedObservable = observable.cache();
        cachedObservable.subscribe(integer -> { // Cached value
            System.out.println("Emitted counter in cached Observer 2: " + integer);
        });
        cachedObservable.subscribe(integer -> { // Cached value
            System.out.println("Emitted counter in cached Observer 3: " + integer);
        });
        // The original observable is still of course there:
        observable.subscribe(integer -> { // Increased value
            System.out.println("Emitted counter in non-cached Observer 4: " + integer);
        });
        cachedObservable.subscribe(integer -> { // Cached value (cache is not modified)
            System.out.println("Emitted counter in cached Observer 5: " + integer);
        });
    }

    private Observable<String> numToString(Integer i) {
        return Observable.just("Number: " + Integer.toString(i));
    }

    @SuppressWarnings("SameParameterValue")
    private Observable<List<String>> query( String query) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubscriptions.unsubscribe();
    }
}
