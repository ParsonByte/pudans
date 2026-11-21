package cn.tolim.pudans.extension.contract.index.query;


public interface LabelCondition extends Condition {

    String INDEX_NAME = "metadata.labels";


    // 返回条件作用的标签键。
    String labelKey();

    // 返回标签条件的否定。
    @Override
    LabelCondition not();
}
