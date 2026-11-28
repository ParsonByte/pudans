package cn.tolim.pudans.communal.mq.consumer;

import cn.tolim.pudans.communal.mq.message.Message;

@FunctionalInterface
public interface MessageListener {

    void onMessage(Message message) throws Exception;
}