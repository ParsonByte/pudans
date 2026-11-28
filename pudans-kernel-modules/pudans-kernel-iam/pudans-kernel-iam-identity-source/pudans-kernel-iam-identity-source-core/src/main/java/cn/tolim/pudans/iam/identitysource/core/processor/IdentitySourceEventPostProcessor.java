package cn.tolim.pudans.iam.identitysource.core.processor;

import cn.tolim.pudans.iam.identitysource.core.processor.modal.IdentitySourceEventProcessData;


public interface IdentitySourceEventPostProcessor {
    // Process data
    // Parameter data IdentitySourceEventProcessData
    void process(IdentitySourceEventProcessData<?> data);
}
