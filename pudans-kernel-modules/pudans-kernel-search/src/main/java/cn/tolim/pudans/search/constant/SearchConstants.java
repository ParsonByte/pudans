package cn.tolim.pudans.search.constant;

public interface SearchConstants {

    // App and index codes are used in URL paths and native SQL binding
    String CODE_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    // Bean validation cap for one batch request
    int MAX_BATCH_SIZE = 500;

    // Maximum number of tag values accepted on one document
    int MAX_TAGS = 50;

    // PostgreSQL text search configuration used for every index
    String TEXT_SEARCH_CONFIGURATION = "simple";
}