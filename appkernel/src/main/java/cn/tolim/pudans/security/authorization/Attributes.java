package cn.tolim.pudans.security.authorization;


public interface Attributes {


    String getVerb();


    boolean isReadOnly();


    String getResource();


    String getSubresource();


    String getName();


    String getApiGroup();


    String getApiVersion();


    boolean isResourceRequest();


    String getPath();

    String getSubName();

    String getUserSpace();
}
