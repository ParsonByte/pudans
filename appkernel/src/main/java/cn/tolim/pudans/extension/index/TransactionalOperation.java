package cn.tolim.pudans.extension.index;


public interface TransactionalOperation {


    void prepare();


    void commit();


    void rollback();
}
