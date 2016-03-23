package com.neoranga55.rxdemo;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.exceptions.Exceptions;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by neoranga on 23/03/2016.
 */
public class RxExperiments {


    public static String deferExceptionDemo() {
        throw new ArrayIndexOutOfBoundsException();
    }

    public static String deferDemo() {
        return "deferDemo completed successfully";
    }

    public static String runRxDemos() {
        // 1- Basic Rx 'Hello world'
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


        // 2- Shorter observable and subscriber
        Observable.just("Short hello, world!").subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                System.out.println(s);
            }
        });


        // 3- Shorten subscriber with lambdas with retrolambda library (lambdas available in N)
        // and basic map transformation
        Observable.just("Short hello, world!").map( s -> s + " By Sebas" ).subscribe( s -> System.out.println(s));


        // 4- Concatenate two observables (both of them will be emitted in sequential order
        // This is equivalent to Observable.concat(observable_1, observable_2)
        Observable.just("First sequential hello, world!").concatWith(  Observable.just("Concatenated hello, world!") ).subscribe( s -> System.out.println(s));


        // 5- Map transformations of multiple types
        Observable.just("Hello, world!")
                .map(s -> s + " By Sebas")
                .map(String::hashCode)
                .map(i -> Integer.toString(i))
                .subscribe(System.out::println);


        // 6- FlatMap transforms each item received (of input type A)
        // into another stream of objects (of output type B)
        // and then flattens (mixes without maintaining order) all new streams
        // into a single stream (of output type B)
        // (there is a new stream/observable produced for each input item received in flatMap)
        //    Warning:    flatMap doesn't respect order (it uses merging),
        // so if flatMap calls an asynchronous method that returns async results
        // the resulting sequence might not respect the sequence of items emitted from
        // original observable before flatMap.
        // To ensure sequence of results use concatMap instead of flatMap
        FillerMethods.query("Hello, world! flatMap")
                .flatMap(Observable::from)
                .subscribe(System.out::println);


        // 7- ConcatMap
        // This will produce the same output as above but ensure sequential order is respected
        FillerMethods.query("Hello, world! concatMap")
                .concatMap(Observable::from)
                .subscribe(System.out::println);


        // 8- Basic difference between map and flatMap
        // same output in this example but flatMap emits Observables instead of Strings
        Observable.just(1,2,3)
                .map(i -> "Num:" + Integer.toString(i)) // Strings
                .subscribe(System.out::println); // Num:1 Num:2 Num:3
        Observable.just(1,2,3)
                .flatMap(FillerMethods::numToString) // Observables
                .subscribe(System.out::println); // Number:1 Number:2 Number:3


        // 9- Advanced FlatMap versus Map
        FillerMethods.query("Hello, world! flatMap VS map")
                .flatMap(Observable::from) // String -> Observable<String> 1, 2... -> String 1, 2...
                .map(FillerMethods::getTitle)
                // Map simply transforms each String item into an Observable of String
                // String1 -> Observable(String2) (emitting Observable, not the String inside)
                .subscribe(System.out::println);
        // Prints each Observable object reference: rx.internal.util.ScalarSynchronousObservable@7e78942

        FillerMethods.query("Hello, world! flatMap + flatMap")
                .flatMap(Observable::from)
                .flatMap(FillerMethods::getTitle)
                // FlatMap simply transforms each String item into an Observable that emits a new String
                // String1 -> Observable(String2) -> String2
                .subscribe(System.out::println);
        // Prints each transformed String: http://Query:Hello,world!flatMap+flatMap0

        FillerMethods.query("Hello, world! flatMap + flatMap + filter + take + store")
                .flatMap(Observable::from)
                .flatMap(FillerMethods::getTitleStream)
                // FlatMap Transforms each String item into an Observable which emits 2 new Strings
                // String1 -> Observable(String2, String3) -> String2, String3
                .filter(title -> title != null)
                .take(11)
                .doOnNext(FillerMethods::storeItem)
                // Execute method on each item emitted before passing it to the subscriber
                .subscribe(System.out::println);
        // Prints two transformed Strings and skips null Strings


        // 10- Caching methods to store items between subscription/un-subscription events
        // useful for surviving Android orientation changes or items among multiple
        rxCache();


        // 11- Retry to subscribe again (from the beginning in cold observables)
        // only when there is an error emitted and based on a policy
        rxRetry();


        return "runRxDemos completed successfully";
    }

    /**
     * Retry demo: it will throw an error every second and
     * it will retry based on the boolean returned (always true here)
     * After 10 seconds it un-subscribes, then the retry stops handling errors/exceptions
     * and it calls subscription onError
     */
    private static void rxRetry() {
        Observable<String> streamWithRetry = Observable.interval(1, TimeUnit.SECONDS)
                .map((tick) -> {
                            if (tick >= 0) {
                                Exceptions.propagate(new IOException("Custom error"));
                            }
                            return tick.toString();
                        }
                )
                .retry((integer, throwable) -> {
                    return true; // Policy to keep retrying (always retry in this example)
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread());
        Subscription retrySubscription = streamWithRetry.subscribe(s -> {
            System.out.println("Subscriber receiving emitted item: " + s);
        }, throwable -> {
            System.out.println("Subscriber receiving error: " + throwable);
        });
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        retrySubscription.unsubscribe();
    }

    /**
     * Cache values and emit always the same from the cache,
     * non cached instances keep receiving new items
     *
     * Note: cache() == replay().autoConnect() (the later has finer control of cache's size)
     * Replay also supports timing expiration of cache
     */
    private static void rxCache() {
        // Create a new observable that emits one integer on each subscribe() call.
        // The counter number indicates how many times the subscribe() has been called.
        Observable<Integer> observable = Observable.create(new Observable.OnSubscribe<Integer>() {
            private int counter = 0;

            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                subscriber.onNext(++counter);
                // Normally it would be better to complete the observable, but for
                // illustrative purposes we'll leave the subscriptions as non-terminating.
                // observer.onCompleted();
            }
        });

        // Subscriptions to the original observable increment the value
        observable.subscribe(integer -> { // Increased value
            System.out.println("Emitted counter in non-cached Observer 1: " + integer);
        });


        // Cache example

        // Create a cached observable that saves all values it receives from
        // the original source and replays it to all of the subscribers
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


        // Replay + Auto Connect example

        // Create a cached observable that saves one value from
        // the original source and replays it to all of the subscribers
        Observable<Integer> replayCacheObservable = observable.replay(1).autoConnect();
        replayCacheObservable.subscribe(integer -> { // Cached value
            System.out.println("Emitted counter in cached (replay().autoConnect()) Observer 6: " + integer);
        });
        replayCacheObservable.subscribe(integer -> { // Cached value
            System.out.println("Emitted counter in cached (replay().autoConnect()) Observer 7: " + integer);
        });
        // The original observable is still of course there:
        observable.subscribe(integer -> { // Increased value
            System.out.println("Emitted counter in non-cached Observer 8: " + integer);
        });
        replayCacheObservable.subscribe(integer -> { // Cached value (cache is not modified)
            System.out.println("Emitted counter in cached (replay().autoConnect()) Observer 9: " + integer);
        });
    }
}
