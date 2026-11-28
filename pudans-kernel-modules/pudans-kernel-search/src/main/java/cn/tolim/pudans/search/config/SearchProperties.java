package cn.tolim.pudans.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = SearchProperties.PREFIX)
public class SearchProperties {

    public static final String PREFIX = "pudans.search";

    // Page size used when the request does not specify one
    private int defaultPageSize = 20;

    // Upper bound accepted for a single page
    private int maxPageSize = 200;

    // Upper bound accepted for a keyword query
    private int maxQueryLength = 500;

    // Upper bound accepted for one batch indexing request
    private int maxBatchSize = 500;

    // Maximum number of characters returned in a search snippet
    private int snippetLength = 160;

    // Create a missing index registry entry when a document is indexed
    private boolean autoCreateIndex = true;

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxQueryLength() {
        return maxQueryLength;
    }

    public void setMaxQueryLength(int maxQueryLength) {
        this.maxQueryLength = maxQueryLength;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getSnippetLength() {
        return snippetLength;
    }

    public void setSnippetLength(int snippetLength) {
        this.snippetLength = snippetLength;
    }

    public boolean isAutoCreateIndex() {
        return autoCreateIndex;
    }

    public void setAutoCreateIndex(boolean autoCreateIndex) {
        this.autoCreateIndex = autoCreateIndex;
    }
}