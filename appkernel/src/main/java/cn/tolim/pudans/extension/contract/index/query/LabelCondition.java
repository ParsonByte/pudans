package cn.tolim.pudans.extension.contract.index.query;


public interface LabelCondition extends Condition {

    String INDEX_NAME = "metadata.labels";


    String labelKey();

    @Override
    LabelCondition not();
}
