package cn.tolim.pudans.repository.service;

import java.io.IOException;
import java.io.InputStream;

// Opens a fresh stream over the upload content on every call
@FunctionalInterface
public interface ContentSource {

    InputStream open() throws IOException;
}