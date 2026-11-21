package cn.tolim.pudans.extension.contract.index.query;


public interface IndexCondition extends Condition {


    // 返回条件作用的索引字段名。
    String indexName();
}
