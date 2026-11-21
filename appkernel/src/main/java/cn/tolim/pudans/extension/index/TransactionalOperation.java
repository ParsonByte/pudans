package cn.tolim.pudans.extension.index;


public interface TransactionalOperation {


    // 预留变更前的状态。
    void prepare();


    // 提交已准备的变更。
    void commit();


    // 回滚已提交的变更。
    void rollback();
}
