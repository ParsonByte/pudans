package cn.tolim.pudans.core.security.authorization;


public class AttributesRecord implements Attributes {
    private final RequestInfo requestInfo;

    public AttributesRecord(RequestInfo requestInfo) {
        this.requestInfo = requestInfo;
    }

    @Override
    public String getVerb() {
        return requestInfo.getVerb();
    }

    @Override
    public String getResource() {
        return requestInfo.getResource();
    }

    @Override
    public String getSubresource() {
        return requestInfo.getSubresource();
    }

    @Override
    public String getName() {
        return requestInfo.getName();
    }

    @Override
    public String getApiGroup() {
        return requestInfo.getApiGroup();
    }

    @Override
    public String getApiVersion() {
        return requestInfo.getApiVersion();
    }

    @Override
    public boolean isResourceRequest() {
        return requestInfo.isResourceRequest();
    }

    @Override
    public String getPath() {
        return requestInfo.getPath();
    }

    @Override
    public String getSubName() {
        return requestInfo.getSubName();
    }
}
