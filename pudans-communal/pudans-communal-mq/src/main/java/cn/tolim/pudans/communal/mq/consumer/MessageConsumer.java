package cn.tolim.pudans.communal.mq.consumer;

public interface MessageConsumer {

    SubscriptionHandle subscribe(MessageSubscription subscription);

    interface SubscriptionHandle extends AutoCloseable {

        @Override
        void close();
    }
}