package com.neoranga55.rxdemo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;

public class FillerMethods {

    public static void storeItem(String s) {
        System.out.println("I'm storing item: " + s);
    }

    public static Observable<String> numToString(Integer i) {
        return Observable.just("Number: " + Integer.toString(i));
    }

    @SuppressWarnings("SameParameterValue")
    public static Observable<List<String>> query(String query) {
        final ArrayList<String> urls = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            urls.add("Query: " + query + " " + i);
        }
        return Observable.just(urls);
    }

    // This method is compatible with map() and flatMap() because
    // it returns an Observable (for flatMap) but it emits only one String (for map)
    public static Observable<String> getTitle(String URL) {
        return Observable.just("http://" + URL.replaceAll(" ",""));
    }

    // This method is only compatible with flatMap() because
    // the returned Observable emits more than one element
    // Each Observable emits the received element twice (with extra text)
    public static Observable<String> getTitleStream(String URL) {
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

    public static Observable<String> getSlowDataFromDB() {
        return Observable.interval(200, TimeUnit.MILLISECONDS).map(s -> "Slow DB data");
    }

    public static Observable<String> getSlowDataFromNetwork() {
        return Observable.interval(300, TimeUnit.MILLISECONDS)
                .map(s -> "Slow network data").first();
    }

    public static Observable<String> getFastDataFromNetwork() {
        return Observable.interval(100, TimeUnit.MILLISECONDS)
                .map(s -> "Super fast network data" + (new Date()).getTime()).take(3);
    }
}
