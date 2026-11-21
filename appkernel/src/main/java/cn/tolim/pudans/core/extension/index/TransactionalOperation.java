package cn.tolim.pudans.core.extension.index;


public interface TransactionalOperation {


    void prepare();


    void commit();


    void rollback();
}
