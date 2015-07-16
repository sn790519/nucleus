package nucleus.presenter.restartable;

import org.junit.Test;

import java.util.ArrayList;

import nucleus.presenter.delivery.DeliverReply;
import nucleus.presenter.delivery.Delivery;
import rx.Notification;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Action2;
import rx.functions.Func0;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static org.junit.Assert.assertFalse;

public class RestartableReplayTest {

    @Test
    public void testReplay() throws Exception {
        PublishSubject<Object> view = PublishSubject.create();
        final TestSubscriber<Delivery<Object, Integer>> testSubscriber = new TestSubscriber<>();
        final ArrayList<Delivery<Object, Integer>> deliveries = new ArrayList<>();

        final PublishSubject<Integer> subject = PublishSubject.create();
        DeliverReply<Object, Integer> restartable = new DeliverReply<>(view);
        Subscription subscription = restartable.call(subject)
            .subscribe(new Action1<Delivery<Object, Integer>>() {
                @Override
                public void call(Delivery<Object, Integer> delivery) {
                    delivery.split(
                        new Action2<Object, Integer>() {
                            @Override
                            public void call(Object o, Integer integer) {
                                testSubscriber.onNext(new Delivery<>(o, Notification.createOnNext(integer)));
                            }
                        },
                        new Action2<Object, Throwable>() {
                            @Override
                            public void call(Object o, Throwable throwable) {
                                testSubscriber.onNext(new Delivery<>(o, Notification.<Integer>createOnError(throwable)));
                            }
                        }
                    );
                }
            });

        // 1-3 values are delivered
        subject.onNext(1);
        subject.onNext(2);
        subject.onNext(3);

        testSubscriber.assertNotCompleted();
        testSubscriber.assertNoValues();

        view.onNext(100);
        deliveries.add(new Delivery<Object, Integer>(100, Notification.createOnNext(1)));
        deliveries.add(new Delivery<Object, Integer>(100, Notification.createOnNext(2)));
        deliveries.add(new Delivery<Object, Integer>(100, Notification.createOnNext(3)));

        testSubscriber.assertValueCount(3);
        testSubscriber.assertNotCompleted();

        // no values delivered if a view has been detached
        view.onNext(null);

        testSubscriber.assertValueCount(3);
        testSubscriber.assertNotCompleted();

        // all values will be be re-delivered to the new view
        view.onNext(101);
        deliveries.add(new Delivery<Object, Integer>(101, Notification.createOnNext(1)));
        deliveries.add(new Delivery<Object, Integer>(101, Notification.createOnNext(2)));
        deliveries.add(new Delivery<Object, Integer>(101, Notification.createOnNext(3)));

        testSubscriber.assertValueCount(6);
        testSubscriber.assertNotCompleted();

        // a throwable will be delivered as well
        Throwable throwable = new Throwable();
        subject.onError(throwable);
        deliveries.add(new Delivery<Object, Integer>(101, Notification.<Integer>createOnError(throwable)));

        testSubscriber.assertValueCount(7);
        testSubscriber.assertNotCompleted();

        // final checks
        testSubscriber.assertValues(deliveries.toArray(new Delivery[deliveries.size()]));

        subscription.unsubscribe();
        assertFalse(subject.hasObservers());
        assertFalse(view.hasObservers());
    }
}