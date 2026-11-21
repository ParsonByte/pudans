package cn.tolim.pudans.extension.api.index.query;


public interface LabelCondition extends Condition {

    String INDEX_NAME = "metadata.labels";


    String labelKey();

    @Override
    LabelCondition not();
}
