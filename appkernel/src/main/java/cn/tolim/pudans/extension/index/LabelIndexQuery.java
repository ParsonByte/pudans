package cn.tolim.pudans.extension.index;

import java.util.Collection;
import java.util.Set;


public interface LabelIndexQuery {


    Set<String> exists(String labelKey);


    Set<String> equal(String labelKey, String labelValue);


    Set<String> notEqual(String labelKey, String labelValue);


    Set<String> in(String labelKey, Collection<String> labelValues);


    Set<String> notIn(String labelKey, Collection<String> labelValues);
}
