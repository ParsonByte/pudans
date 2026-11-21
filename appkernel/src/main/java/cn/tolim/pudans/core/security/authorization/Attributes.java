package cn.tolim.pudans.core.security.authorization;


public interface Attributes {


    String getVerb();


    String getResource();


    String getSubresource();


    String getName();


    String getApiGroup();


    String getApiVersion();


    boolean isResourceRequest();


    String getPath();

    String getSubName();
}
