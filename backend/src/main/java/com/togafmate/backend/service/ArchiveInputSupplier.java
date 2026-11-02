package com.togafmate.backend.service;

import java.io.IOException;
import java.io.InputStream;

/** Opens a repeatable input stream over an uploaded archive. */
@FunctionalInterface
public interface ArchiveInputSupplier {
    /** Opens a fresh stream. */
    InputStream open() throws IOException;
}
